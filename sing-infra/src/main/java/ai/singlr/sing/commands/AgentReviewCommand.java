/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import ai.singlr.sing.config.SingYaml;
import ai.singlr.sing.config.YamlUtil;
import ai.singlr.sing.engine.Banner;
import ai.singlr.sing.engine.ContainerExec;
import ai.singlr.sing.engine.ContainerManager;
import ai.singlr.sing.engine.ContainerState;
import ai.singlr.sing.engine.NameValidator;
import ai.singlr.sing.engine.ShellExecutor;
import ai.singlr.sing.engine.SingPaths;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
    name = "review",
    description = "Run a code review audit on the project's code changes.",
    mixinStandardHelpOptions = true)
public final class AgentReviewCommand implements Runnable {

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
    CliCommand.run(spec, this::execute);
  }

  private void execute() throws Exception {
    NameValidator.requireValidProjectName(name);

    var singYamlPath = SingPaths.resolveSingYaml(name, file);
    if (!Files.exists(singYamlPath)) {
      throw new IllegalStateException(
          "Project descriptor not found: "
              + singYamlPath.toAbsolutePath()
              + "\n  Create a sing.yaml in the current directory, or specify one with --file.");
    }
    var config = SingYaml.fromMap(YamlUtil.parseFile(singYamlPath));

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

    var sshUser = config.sshUser();
    var scriptPath = "/home/" + sshUser + "/.sing/code-review.sh";

    var check = shell.exec(ContainerExec.asDevUser(name, List.of("test", "-f", scriptPath)));
    if (!check.ok()) {
      throw new IllegalStateException(
          "No code review script found at "
              + scriptPath
              + "\n  Run 'sing agent context regen "
              + name
              + "' to generate it."
              + "\n  Ensure code_review.enabled is true in sing.yaml.");
    }

    var reviewCmd = ContainerExec.asDevUser(name, List.of("bash", scriptPath));
    var result = shell.exec(reviewCmd);

    if (json) {
      var map = new LinkedHashMap<String, Object>();
      map.put("name", name);
      map.put("action", "review");
      map.put("result", result.ok() ? "pass" : "fail");
      map.put("exit_code", result.exitCode());
      if (!result.ok()) {
        map.put("details_path", "/home/" + sshUser + "/code-review.md");
      }
      if (!result.stdout().isBlank()) {
        map.put("output", result.stdout().strip());
      }
      System.out.println(YamlUtil.dumpJson(map));
      return;
    }

    Banner.printBranding(System.out, Ansi.AUTO);
    System.out.println();

    if (result.ok()) {
      System.out.println(Ansi.AUTO.string("  @|bold,green \u2713 Code review passed.|@"));
    } else {
      System.out.println(Ansi.AUTO.string("  @|bold,red \u2717 Code review found issues.|@"));
      System.out.println(
          Ansi.AUTO.string("  @|faint Details saved to:|@ /home/" + sshUser + "/code-review.md"));
      if (!result.stderr().isBlank()) {
        System.err.println(result.stderr().strip());
      }
    }
  }
}
