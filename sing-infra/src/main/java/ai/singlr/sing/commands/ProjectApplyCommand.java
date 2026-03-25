/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import ai.singlr.sing.config.SingYaml;
import ai.singlr.sing.config.YamlUtil;
import ai.singlr.sing.engine.Banner;
import ai.singlr.sing.engine.ContainerManager;
import ai.singlr.sing.engine.ContainerState;
import ai.singlr.sing.engine.NameValidator;
import ai.singlr.sing.engine.ProjectApplier;
import ai.singlr.sing.engine.ProjectProvisioner;
import ai.singlr.sing.engine.ShellExecutor;
import ai.singlr.sing.engine.SingPaths;
import java.nio.file.Files;
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
    name = "apply",
    description = "Apply incremental changes from sing.yaml to a running project.",
    mixinStandardHelpOptions = true)
public final class ProjectApplyCommand implements Runnable {

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

  @Option(
      names = "--git-token",
      description = "Access token for cloning private repos over HTTPS.",
      defaultValue = "${GITHUB_TOKEN}")
  private String gitToken;

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
              "Project '" + name + "' is stopped. Start it with: sing up " + name);
      case ContainerState.NotCreated ignored ->
          throw new IllegalStateException(
              "Project '"
                  + name
                  + "' does not exist. Run 'sing project create "
                  + name
                  + "' first.");
      case ContainerState.Error e ->
          throw new IllegalStateException("Container error: " + e.message());
    }

    var ansi = Ansi.AUTO;
    if (!json) {
      Banner.printBranding(System.out, ansi);
      System.out.println();
      System.out.println(ansi.string("  @|bold Applying changes to|@ " + name + "..."));
      System.out.println();
    }

    var applier = new ProjectApplier(shell, System.out);
    var sshUser = config.sshUser();
    var token = gitToken != null && !gitToken.isBlank() ? gitToken : null;

    var totalAdded = 0;
    var totalRemoved = 0;
    var totalSkipped = 0;

    var warnings = applier.checkUnsupportedChanges(config);

    var svcResult = applier.applyServices(name, config.services());
    totalAdded += svcResult.added();
    totalSkipped += svcResult.skipped();

    var reconcileResult = applier.reconcileServices(name, config.services());
    totalRemoved += reconcileResult.removed();

    var repoResult =
        applier.applyRepos(
            name, config.repos(), sshUser, ProjectProvisioner.singleTokenMap(token), config.git());
    totalAdded += repoResult.added();
    totalSkipped += repoResult.skipped();

    var filesResult = applier.applyWorkspaceFiles(name, singYamlPath, sshUser);
    totalAdded += filesResult.added();
    totalSkipped += filesResult.skipped();

    var agentInstall =
        config.agent() != null
            ? Objects.requireNonNullElse(config.agent().install(), List.of(config.agent().type()))
            : null;
    var agentResult = applier.applyAgentTools(name, agentInstall, config.runtimes());
    totalAdded += agentResult.added();
    totalSkipped += agentResult.skipped();

    var gitResult = applier.applyGitConfig(name, config.git(), sshUser);
    totalAdded += gitResult.added();
    totalSkipped += gitResult.skipped();

    var ctxResult = applier.applyAgentContext(name, config);
    totalAdded += ctxResult.added();
    totalSkipped += ctxResult.skipped();

    if (json) {
      var map = new LinkedHashMap<String, Object>();
      map.put("name", name);
      map.put("action", "apply");
      map.put("added", totalAdded);
      map.put("removed", totalRemoved);
      map.put("skipped", totalSkipped);
      if (!warnings.isEmpty()) {
        map.put("warnings", warnings);
      }
      System.out.println(YamlUtil.dumpJson(map));
      return;
    }

    System.out.println();
    for (var warning : warnings) {
      System.out.println(ansi.string("  @|yellow \u26a0|@ " + warning));
    }
    System.out.println(
        ansi.string(
            "  @|bold,green \u2713 Apply complete:|@ "
                + totalAdded
                + " added, "
                + totalRemoved
                + " removed, "
                + totalSkipped
                + " skipped"));
  }
}
