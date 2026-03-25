/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import ai.singlr.sing.config.SingYaml;
import ai.singlr.sing.config.Spec;
import ai.singlr.sing.config.SpecDirectory;
import ai.singlr.sing.config.YamlUtil;
import ai.singlr.sing.engine.AgentCli;
import ai.singlr.sing.engine.AgentSession;
import ai.singlr.sing.engine.Banner;
import ai.singlr.sing.engine.ContainerExec;
import ai.singlr.sing.engine.ContainerManager;
import ai.singlr.sing.engine.ContainerState;
import ai.singlr.sing.engine.NameValidator;
import ai.singlr.sing.engine.ShellExecutor;
import ai.singlr.sing.engine.SingPaths;
import ai.singlr.sing.engine.SnapshotManager;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Dispatches the next ready spec to an agent for autonomous execution. Reads {@code
 * specs/index.yaml} from the container, finds the next pending spec (respecting dependencies and
 * assignee), reads its {@code spec.md}, and launches the configured agent.
 */
@Command(
    name = "dispatch",
    description = "Dispatch the next ready spec to an agent for autonomous execution.",
    mixinStandardHelpOptions = true)
public final class DispatchCommand implements Runnable {

  @Parameters(index = "0", description = "Project name.")
  private String name;

  @Option(
      names = "--spec",
      description = "Override auto-selection: dispatch a specific spec by ID.")
  private String specId;

  @Option(names = "--background", description = "Run agent in background.", defaultValue = "true")
  private boolean background;

  @Option(names = "--dry-run", description = "Print commands instead of executing them.")
  private boolean dryRun;

  @Option(names = "--json", description = "Output in JSON format.")
  private boolean json;

  @Option(
      names = {"-f", "--file"},
      description = "Path to sing.yaml project descriptor.",
      defaultValue = "sing.yaml")
  private String file;

  @picocli.CommandLine.Spec private CommandSpec commandSpec;

  @Override
  public void run() {
    try {
      execute();
    } catch (Exception e) {
      var msg = Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName());
      System.err.println(Banner.errorLine(msg, Ansi.AUTO));
      throw new picocli.CommandLine.ExecutionException(commandSpec.commandLine(), msg, e);
    }
  }

  private void execute() throws Exception {
    NameValidator.requireValidProjectName(name);

    if (!json) {
      Banner.printBranding(System.out, Ansi.AUTO);
    }

    var shell = new ShellExecutor(dryRun);
    var mgr = new ContainerManager(shell);

    var state = mgr.queryState(name);
    switch (state) {
      case ContainerState.Running ignored -> {}
      case ContainerState.Stopped ignored ->
          throw new IllegalStateException(
              "Project '" + name + "' is stopped. Start it with: sing up " + name);
      case ContainerState.NotCreated ignored ->
          throw new IllegalStateException(
              "Project '" + name + "' does not exist. Run 'sing project create' first.");
      case ContainerState.Error e ->
          throw new IllegalStateException("Container error: " + e.message());
    }

    var singYamlPath = SingPaths.resolveSingYaml(name, file);
    if (!Files.exists(singYamlPath)) {
      throw new IllegalStateException(
          "Project descriptor not found: " + singYamlPath.toAbsolutePath());
    }
    var config = SingYaml.fromMap(YamlUtil.parseFile(singYamlPath));

    if (config.agent() == null || config.agent().specsDir() == null) {
      throw new IllegalStateException(
          "No specs_dir configured in agent block. Add 'specs_dir: specs' to sing.yaml.");
    }

    var sshUser = config.sshUser();
    var specsDir = "/home/" + sshUser + "/workspace/" + config.agent().specsDir();

    var specs = readSpecIndex(shell, specsDir);
    if (specs.isEmpty()) {
      printNoSpecs();
      return;
    }

    var agentSession = new AgentSession(shell);
    var existingSession = agentSession.queryStatus(name);
    if (existingSession != null && existingSession.running()) {
      throw new IllegalStateException(
          "Agent is already running on '"
              + name
              + "' (PID "
              + existingSession.pid()
              + "). Stop it first with: sing agent stop "
              + name);
    }

    var nextSpec = resolveSpec(specs);
    if (nextSpec == null) {
      printNoSpecs();
      return;
    }

    var specBody = readSpecMd(shell, specsDir, nextSpec.id());
    var description = !specBody.isBlank() ? specBody : nextSpec.title();
    var task = buildTaskPrompt(nextSpec, description, config.agent().specsDir());

    if (json) {
      var map = new LinkedHashMap<String, Object>();
      map.put("name", name);
      map.put("spec_id", nextSpec.id());
      map.put("spec_title", nextSpec.title());
      map.put("mode", background ? "background" : "foreground");
      map.put("task", task);
      System.out.println(YamlUtil.dumpJson(map));
      if (dryRun) {
        return;
      }
    }

    if (!json) {
      System.out.println(Ansi.AUTO.string("  @|bold Dispatching spec:|@ " + nextSpec.id()));
      System.out.println(Ansi.AUTO.string("  @|faint " + nextSpec.title() + "|@"));
      System.out.println();
    }

    var agentType = config.agent().type();
    var agentCli = AgentCli.fromYamlName(agentType);
    var workDir = "/home/" + sshUser + "/workspace";
    var fullPermissions =
        config.agent().config() != null
            && "full".equals(config.agent().config().get("permissions"));

    var label = SnapshotManager.defaultLabel();
    String branchName = null;

    if (config.agent().autoSnapshot()) {
      var snapMgr = new SnapshotManager(shell);
      if (!json) {
        System.out.println(Ansi.AUTO.string("  @|bold Auto-snapshot:|@ " + label + "..."));
      }
      snapMgr.create(name, label);
      if (!json) {
        Banner.printSnapshotCreated(name, label, System.out, Ansi.AUTO);
        System.out.println();
      }
    }

    if (config.agent().autoBranch()) {
      var prefix = config.agent().branchPrefix() != null ? config.agent().branchPrefix() : "sing/";
      branchName = nextSpec.branch() != null ? nextSpec.branch() : prefix + nextSpec.id();
      if (!json) {
        System.out.println(Ansi.AUTO.string("  @|bold Creating branch:|@ " + branchName + "..."));
      }
      var branchCmd =
          ContainerExec.asDevUser(
              name, List.of("git", "-C", workDir, "checkout", "-b", branchName));
      var result = shell.exec(branchCmd);
      if (!result.ok()) {
        throw new IOException("Failed to create branch '" + branchName + "': " + result.stderr());
      }
      if (!json) {
        System.out.println(Ansi.AUTO.string("  @|green \u2713|@ Branch " + branchName));
        System.out.println();
      }
    }

    agentSession.ensureDirectory(name);
    agentSession.writeTaskFile(name, task);
    agentSession.writeSession(name, task, Objects.requireNonNullElse(branchName, ""));

    if (background) {
      var sshCmd =
          AgentSession.buildBackgroundLaunchCommand(
              name, sshUser, workDir, fullPermissions, agentCli);
      if (!json) {
        System.out.println(Ansi.AUTO.string("  @|bold Launching agent in background...|@"));
        System.out.println(Ansi.AUTO.string("  @|faint " + String.join(" ", sshCmd) + "|@"));
        System.out.println();
      }
      if (!dryRun) {
        var pb = new ProcessBuilder(sshCmd);
        pb.inheritIO();
        var process = pb.start();
        process.waitFor();
      }
      Banner.printAgentLaunched(name, task, branchName, System.out, Ansi.AUTO);
      if (!dryRun) {
        launchWatcherIfGuardrails(config);
      }
    } else {
      var sshCmd =
          AgentSession.buildForegroundTaskCommand(
              name, sshUser, workDir, fullPermissions, agentCli);
      if (!json) {
        System.out.println(Ansi.AUTO.string("  @|bold Launching agent with spec...|@"));
        System.out.println(Ansi.AUTO.string("  @|faint " + String.join(" ", sshCmd) + "|@"));
        System.out.println();
      }
      if (!dryRun) {
        var pb = new ProcessBuilder(sshCmd);
        pb.inheritIO();
        var process = pb.start();
        var exitCode = process.waitFor();
        if (exitCode != 0) {
          System.err.println(
              Banner.errorLine("Agent session exited with code " + exitCode, Ansi.AUTO));
        }
      }
    }
  }

  private List<Spec> readSpecIndex(ShellExecutor shell, String specsDir) throws Exception {
    var catCmd = ContainerExec.asDevUser(name, List.of("cat", specsDir + "/index.yaml"));
    var result = shell.exec(catCmd);
    if (!result.ok() || result.stdout().isBlank()) {
      return List.of();
    }
    return SpecDirectory.parseIndex(YamlUtil.parseMap(result.stdout()));
  }

  private String readSpecMd(ShellExecutor shell, String specsDir, String specId) throws Exception {
    var catCmd =
        ContainerExec.asDevUser(name, List.of("cat", specsDir + "/" + specId + "/spec.md"));
    var result = shell.exec(catCmd);
    return result.ok() ? result.stdout().strip() : "";
  }

  private Spec resolveSpec(List<Spec> specs) {
    if (specId != null) {
      return specs.stream()
          .filter(s -> s.id().equals(specId))
          .findFirst()
          .orElseThrow(
              () -> new IllegalArgumentException("Spec '" + specId + "' not found in index.yaml"));
    }
    return SpecDirectory.nextReady(specs);
  }

  static String buildTaskPrompt(Spec spec, String description, String specsDir) {
    return "Your current spec: \""
        + spec.title()
        + "\" (id: "
        + spec.id()
        + ").\n\n"
        + description
        + "\n\nWhen complete, update "
        + specsDir
        + "/index.yaml and set this spec's status to \"done\"."
        + " Then pick up the next pending spec and continue working.";
  }

  private void printNoSpecs() {
    if (json) {
      var map = new LinkedHashMap<String, Object>();
      map.put("name", name);
      map.put("dispatched", false);
      map.put("reason", "no_pending_specs");
      System.out.println(YamlUtil.dumpJson(map));
    } else {
      System.out.println(Ansi.AUTO.string("  @|faint No pending specs found for " + name + ".|@"));
    }
  }

  private void launchWatcherIfGuardrails(SingYaml config) {
    if (config.agent() == null || config.agent().guardrails() == null) {
      return;
    }
    try {
      var singBinary = SingPaths.binaryPath().toString();
      var singYamlPath = SingPaths.resolveSingYaml(name, file);
      var cmd =
          List.of(
              "nohup",
              singBinary,
              "agent",
              "watch",
              name,
              "-f",
              singYamlPath.toAbsolutePath().toString());
      var watchLog = SingPaths.projectDir(name).resolve("watch.log");
      Files.createDirectories(watchLog.getParent());
      var pb = new ProcessBuilder(cmd);
      pb.redirectOutput(ProcessBuilder.Redirect.to(watchLog.toFile()));
      pb.redirectErrorStream(true);
      pb.start();
      System.out.println(
          Ansi.AUTO.string("  @|green \u2713|@ Guardrail watcher started (log: " + watchLog + ")"));
    } catch (Exception e) {
      System.err.println(
          Banner.errorLine(
              "Failed to start guardrail watcher: "
                  + e.getMessage()
                  + ". Run manually: sing agent watch "
                  + name,
              Ansi.AUTO));
    }
  }
}
