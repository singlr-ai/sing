/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import ai.singlr.sing.config.SingYaml;
import ai.singlr.sing.config.Spec;
import ai.singlr.sing.config.SpecDirectory;
import ai.singlr.sing.config.SpecScaffold;
import ai.singlr.sing.config.YamlUtil;
import ai.singlr.sing.engine.Banner;
import ai.singlr.sing.engine.ContainerManager;
import ai.singlr.sing.engine.ContainerState;
import ai.singlr.sing.engine.NameValidator;
import ai.singlr.sing.engine.ShellExecutor;
import ai.singlr.sing.engine.SingPaths;
import ai.singlr.sing.engine.SpecWorkspace;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "create",
    description = "Create a spec scaffold and add it to index.yaml.",
    mixinStandardHelpOptions = true)
public final class SpecCreateCommand implements Runnable {

  @Parameters(index = "0", description = "Project name.")
  private String name;

  @Option(names = "--title", required = true, description = "Spec title.")
  private String title;

  @Option(names = "--id", description = "Spec id. Defaults to a slug derived from the title.")
  private String specId;

  @Option(names = "--status", description = "Initial status.", defaultValue = "pending")
  private String status;

  @Option(names = "--assignee", description = "Assignee.")
  private String assignee;

  @Option(names = "--branch", description = "Branch name.")
  private String branch;

  @Option(names = "--depends-on", split = ",", description = "Comma-separated dependency ids.")
  private List<String> dependsOn;

  @Option(
      names = {"-f", "--file"},
      description = "Path to sing.yaml project descriptor.",
      defaultValue = "sing.yaml")
  private String file;

  @Option(names = "--json", description = "Output in JSON format.")
  private boolean json;

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
    SpecDirectory.requireValidStatus(status);
    if (branch != null) {
      NameValidator.requireValidGitRef(branch, "spec.branch");
    }

    var resolvedSpecId = specId != null ? specId : SpecScaffold.deriveId(title);
    NameValidator.requireValidSpecId(resolvedSpecId);
    var resolvedDependsOn = dependsOn != null ? List.copyOf(dependsOn) : List.<String>of();
    resolvedDependsOn.forEach(NameValidator::requireValidSpecId);

    var shell = new ShellExecutor(false);
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
      throw new IllegalStateException(
          "Project descriptor not found: " + singYamlPath.toAbsolutePath());
    }
    var config = SingYaml.fromMap(YamlUtil.parseFile(singYamlPath));
    if (config.agent() == null || config.agent().specsDir() == null) {
      throw new IllegalStateException(
          "No specs_dir configured in agent block. Add 'specs_dir: specs' to sing.yaml.");
    }

    var workspace =
        new SpecWorkspace(
            shell, name, "/home/" + config.sshUser() + "/workspace/" + config.agent().specsDir());
    var spec = new Spec(resolvedSpecId, title.strip(), status, assignee, resolvedDependsOn, branch);
    workspace.createSpec(spec, SpecScaffold.markdownTemplate(title));

    if (json) {
      var map = new LinkedHashMap<String, Object>();
      map.put("name", name);
      map.put("created", true);
      map.put("spec", spec.toMap());
      map.put("spec_path", workspace.specMarkdownPath(spec.id()));
      System.out.println(YamlUtil.dumpJson(map));
      return;
    }

    Banner.printBranding(System.out, Ansi.AUTO);
    System.out.println(
        Ansi.AUTO.string(
            "  @|green \u2713|@ Created spec "
                + spec.id()
                + " at "
                + workspace.specMarkdownPath(spec.id())));
  }
}
