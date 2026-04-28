/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import ai.singlr.sing.config.SingYaml;
import ai.singlr.sing.config.YamlUtil;
import ai.singlr.sing.engine.AgentCli;
import ai.singlr.sing.engine.AgentSession;
import ai.singlr.sing.engine.Banner;
import ai.singlr.sing.engine.ContainerManager;
import ai.singlr.sing.engine.ContainerState;
import ai.singlr.sing.engine.NameValidator;
import ai.singlr.sing.engine.ShellExecutor;
import ai.singlr.sing.engine.SingPaths;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
    name = "sweep",
    description = "Run an entropy sweep to clean up codebase drift and inconsistencies.",
    mixinStandardHelpOptions = true)
public final class AgentSweepCommand implements Runnable {

  static final String SWEEP_PROMPT =
      """
      You are running an entropy sweep — a focused cleanup pass on this codebase. \
      Do NOT add new features or change behavior. Only clean up.

      Scan for and fix:
      1. Dead imports and unused variables
      2. Naming inconsistencies (methods, variables, files that don't match project conventions)
      3. Dead code (unreachable branches, commented-out code, unused methods)
      4. Documentation drift (outdated comments, stale README sections, wrong examples)
      5. Dependency issues (unused dependencies, version inconsistencies)
      6. Test coverage gaps for critical paths (add tests, don't modify production code)
      7. Formatting and style violations per project conventions

      For each category, scan the entire codebase systematically. Fix issues directly — \
      don't just report them. Run tests after each batch of fixes to ensure nothing breaks. \
      Commit each category as a separate commit with a clear message like \
      "sweep: remove dead imports" or "sweep: fix naming inconsistencies".

      When done, write a summary to ~/sweep-report.md listing what was found and fixed.""";

  @Parameters(index = "0", description = "Project name.")
  private String name;

  @Option(
      names = {"-f", "--file"},
      description = "Path to sing.yaml project descriptor.",
      defaultValue = "sing.yaml")
  private String file;

  @Option(names = "--json", description = "Output in JSON format.")
  private boolean json;

  @Option(names = "--dry-run", description = "Print commands instead of executing them.")
  private boolean dryRun;

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
    SingYaml config = null;
    if (Files.exists(singYamlPath)) {
      config = SingYaml.fromMap(YamlUtil.parseFile(singYamlPath));
    }

    var sshUser = config != null ? config.sshUser() : "dev";
    var workDir = "/home/" + sshUser + "/workspace";
    var agentType =
        config != null && config.agent() != null ? config.agent().type() : "claude-code";
    var agentCli = AgentCli.fromYamlName(agentType);
    var fullPermissions =
        config != null
            && config.agent() != null
            && config.agent().config() != null
            && "full".equals(config.agent().config().get("permissions"));

    var agentSession = new AgentSession(shell);
    agentSession.ensureDirectory(name);
    agentSession.writeTaskFile(name, SWEEP_PROMPT);

    var sshCmd =
        AgentSession.buildForegroundTaskCommand(name, sshUser, workDir, fullPermissions, agentCli);

    if (json) {
      var map = new LinkedHashMap<String, Object>();
      map.put("name", name);
      map.put("action", "sweep");
      map.put("agent", agentType);
      map.put("ssh_command", String.join(" ", sshCmd));
      System.out.println(YamlUtil.dumpJson(map));
      return;
    }

    if (!dryRun) {
      Banner.printBranding(System.out, Ansi.AUTO);
    }
    System.out.println();
    System.out.println(
        Ansi.AUTO.string("  @|bold Launching entropy sweep with " + agentType + "...|@"));
    System.out.println(Ansi.AUTO.string("  @|faint " + String.join(" ", sshCmd) + "|@"));
    System.out.println();

    if (dryRun) {
      System.out.println("[dry-run] " + String.join(" ", sshCmd));
    } else {
      var pb = new ProcessBuilder(sshCmd);
      pb.inheritIO();
      var process = pb.start();
      var exitCode = process.waitFor();
      if (exitCode != 0) {
        System.err.println(
            Banner.errorLine("Sweep session exited with code " + exitCode, Ansi.AUTO));
      } else {
        System.out.println(Ansi.AUTO.string("  @|bold,green \u2713 Entropy sweep complete.|@"));
        System.out.println(
            Ansi.AUTO.string("  @|faint Report at:|@ /home/" + sshUser + "/sweep-report.md"));
      }
    }
  }
}
