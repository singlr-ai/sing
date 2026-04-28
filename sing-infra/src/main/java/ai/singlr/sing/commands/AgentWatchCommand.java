/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import ai.singlr.sing.config.Guardrails;
import ai.singlr.sing.config.Notifications;
import ai.singlr.sing.config.SingYaml;
import ai.singlr.sing.config.YamlUtil;
import ai.singlr.sing.engine.AgentSession;
import ai.singlr.sing.engine.Banner;
import ai.singlr.sing.engine.ContainerExec;
import ai.singlr.sing.engine.ContainerManager;
import ai.singlr.sing.engine.ContainerState;
import ai.singlr.sing.engine.GuardrailChecker;
import ai.singlr.sing.engine.NameValidator;
import ai.singlr.sing.engine.ShellExecutor;
import ai.singlr.sing.engine.SingPaths;
import ai.singlr.sing.engine.SnapshotManager;
import ai.singlr.sing.engine.WebhookNotifier;
import java.nio.file.Files;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
    name = "watch",
    description = "Monitor a running agent and enforce guardrails.",
    mixinStandardHelpOptions = true)
public final class AgentWatchCommand implements Runnable {

  @Parameters(index = "0", description = "Project name.")
  private String name;

  @Option(names = "--interval", description = "Check interval (e.g. 5m, 30s).", defaultValue = "5m")
  private String interval;

  @Option(names = "--dry-run", description = "Print actions instead of executing them.")
  private boolean dryRun;

  @Option(names = "--json", description = "Output in JSON format.")
  private boolean json;

  @Option(
      names = {"-f", "--file"},
      description = "Path to sing.yaml project descriptor.",
      defaultValue = "sing.yaml")
  private String file;

  @Spec private CommandSpec spec;

  @Override
  public void run() {
    try {
      execute();
    } catch (Exception e) {
      var msg = Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName());
      System.err.println(Banner.errorLine(msg, Ansi.AUTO));
      throw new picocli.CommandLine.ExecutionException(spec.commandLine(), msg, e);
    }
  }

  private void execute() throws Exception {
    NameValidator.requireValidProjectName(name);
    var shell = new ShellExecutor(dryRun);
    var mgr = new ContainerManager(shell);

    var state = mgr.queryState(name);
    switch (state) {
      case ContainerState.Running ignored -> {}
      case ContainerState.Stopped ignored ->
          throw new IllegalStateException(
              "Project '" + name + "' is stopped. Start it with: sing project start " + name);
      case ContainerState.NotCreated ignored ->
          throw new IllegalStateException(
              "Project '" + name + "' does not exist. Run 'sing project create' first.");
      case ContainerState.Error e ->
          throw new IllegalStateException("Container error: " + e.message());
    }

    var singYamlPath = SingPaths.resolveSingYaml(name, file);
    if (!Files.exists(singYamlPath)) {
      throw new IllegalStateException("No sing.yaml found at " + file);
    }
    var config = SingYaml.fromMap(YamlUtil.parseFile(singYamlPath));
    if (config.agent() == null || config.agent().guardrails() == null) {
      throw new IllegalStateException(
          "No guardrails configured. Add a guardrails block to the agent section in sing.yaml.");
    }
    var guardrails = config.agent().guardrails();

    var notifications = config.agent().notifications();
    WebhookNotifier notifier = null;
    if (notifications != null && notifications.url() != null) {
      notifier = new WebhookNotifier(notifications.url());
    }

    var agentSession = new AgentSession(shell);
    var sessionInfo = agentSession.queryStatus(name);
    if (sessionInfo == null || !sessionInfo.running()) {
      throw new IllegalStateException(
          "No agent session running. Launch one with: sing agent start "
              + name
              + " --background --task '...'");
    }

    Instant startedAt;
    try {
      startedAt =
          !sessionInfo.startedAt().isBlank()
              ? Instant.parse(sessionInfo.startedAt())
              : Instant.now();
    } catch (java.time.format.DateTimeParseException e) {
      startedAt = Instant.now();
    }

    var repoPaths = config.repoPaths();

    var intervalDuration = Guardrails.parseDuration(interval);
    if (intervalDuration == null) {
      throw new IllegalArgumentException("Invalid interval: " + interval);
    }
    var intervalMs = intervalDuration.toMillis();

    var checker = new GuardrailChecker(shell);

    if (!json) {
      System.out.println(
          Ansi.AUTO.string(
              "  @|bold Watching|@ "
                  + name
                  + " @|faint (interval: "
                  + interval
                  + ", max: "
                  + Objects.requireNonNullElse(guardrails.maxDuration(), "-")
                  + ")|@"));
    }

    while (true) {
      Thread.sleep(intervalMs);

      var currentInfo = agentSession.queryStatus(name);
      if (currentInfo == null || !currentInfo.running()) {
        if (json) {
          var map = new LinkedHashMap<String, Object>();
          map.put("name", name);
          map.put("triggered", false);
          map.put("reason", "agent_exited");
          System.out.println(YamlUtil.dumpJson(map));
        } else {
          System.out.println(Ansi.AUTO.string("  @|faint Agent exited. Watch complete.|@"));
        }
        sendNotification(
            notifier,
            notifications,
            "agent_exited",
            name,
            "Agent exited",
            "Agent process is no longer running. Run: sing agent report " + name);
        return;
      }

      var result = checker.check(name, guardrails, startedAt, repoPaths);

      if (result instanceof GuardrailChecker.GuardrailResult.Ok) {
        if (!json) {
          var elapsed =
              GuardrailChecker.formatDuration(java.time.Duration.between(startedAt, Instant.now()));
          System.out.println(
              Ansi.AUTO.string("  @|green \u2713|@ @|faint [" + elapsed + "] Agent active.|@"));
        }
        continue;
      }

      var triggered = (GuardrailChecker.GuardrailResult.Triggered) result;
      var elapsed =
          GuardrailChecker.formatDuration(java.time.Duration.between(startedAt, Instant.now()));

      writeTriggerFile(shell, name, triggered, elapsed);

      var snapshotLabel = "";
      switch (triggered.action()) {
        case "snapshot-and-stop" -> {
          if (!dryRun) {
            var snapMgr = new SnapshotManager(shell);
            snapshotLabel = "guardrail-" + SnapshotManager.defaultLabel().substring(5);
            snapMgr.create(name, snapshotLabel);
            agentSession.killAgent(name);
          }
        }
        case "stop" -> {
          if (!dryRun) {
            agentSession.killAgent(name);
          }
        }
        case "notify" -> {}
        default -> {}
      }

      if (json) {
        var map = new LinkedHashMap<String, Object>();
        map.put("name", name);
        map.put("triggered", true);
        map.put("reason", triggered.reason());
        map.put("detail", triggered.detail());
        map.put("action", triggered.action());
        map.put("elapsed", elapsed);
        if (!snapshotLabel.isEmpty()) {
          map.put("snapshot", snapshotLabel);
        }
        System.out.println(YamlUtil.dumpJson(map));
      } else {
        System.out.println(
            Ansi.AUTO.string(
                "  @|bold,red \u2717|@ @|bold ["
                    + elapsed
                    + "] Guardrail triggered:|@ "
                    + triggered.reason()));
        System.out.println(Ansi.AUTO.string("    " + triggered.detail()));
        System.out.println(Ansi.AUTO.string("    @|bold Action:|@ " + triggered.action()));
        if (!snapshotLabel.isEmpty()) {
          System.out.println(Ansi.AUTO.string("    @|bold Snapshot:|@ " + snapshotLabel));
        }
      }

      sendNotification(
          notifier,
          notifications,
          "guardrail_triggered",
          name,
          "Guardrail: " + triggered.reason(),
          triggered.detail() + ". Action: " + triggered.action());

      if (!"notify".equals(triggered.action())) {
        sendNotification(
            notifier,
            notifications,
            "session_done",
            name,
            "Watch complete",
            "Agent session ended. Run: sing agent report " + name);
        return;
      }
    }
  }

  private static void writeTriggerFile(
      ShellExecutor shell,
      String containerName,
      GuardrailChecker.GuardrailResult.Triggered triggered,
      String elapsed)
      throws Exception {
    var map = new LinkedHashMap<String, Object>();
    map.put("triggered_at", Instant.now().toString());
    map.put("reason", triggered.reason());
    map.put("detail", triggered.detail());
    map.put("action", triggered.action());
    var yaml = YamlUtil.dumpToString(map);
    var cmd =
        ContainerExec.asDevUser(
            containerName,
            List.of(
                "bash",
                "-c",
                "printf '%s' \"$1\" > /home/dev/guardrail-triggered.yaml",
                "bash",
                yaml));
    shell.exec(cmd);
  }

  private static void sendNotification(
      WebhookNotifier notifier,
      Notifications notifications,
      String event,
      String project,
      String title,
      String message) {
    if (notifier != null && (notifications == null || notifications.shouldNotify(event))) {
      notifier.notify(event, project, title, message);
    }
  }
}
