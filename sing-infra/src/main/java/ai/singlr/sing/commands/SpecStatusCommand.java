/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import ai.singlr.sing.config.SingYaml;
import ai.singlr.sing.config.SpecDirectory;
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
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "status",
    description = "Update a spec's lifecycle status.",
    mixinStandardHelpOptions = true)
public final class SpecStatusCommand implements Runnable {

  @Parameters(index = "0", description = "Project name.")
  private String name;

  @Parameters(index = "1", description = "Spec id.")
  private String specId;

  @Parameters(index = "2", description = "New status.")
  private String status;

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
    CliCommand.run(commandSpec, this::execute);
  }

  private void execute() throws Exception {
    NameValidator.requireValidProjectName(name);
    NameValidator.requireValidSpecId(specId);
    SpecDirectory.requireValidStatus(status);

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
    var updated = workspace.updateStatus(specId, status);
    var summary = SpecDirectory.summarize(workspace.readIndex());

    if (json) {
      var map = new LinkedHashMap<String, Object>();
      map.put("name", name);
      map.put("spec", updated.toMap());
      map.put("summary", summary.toMap());
      System.out.println(YamlUtil.dumpJson(map));
      return;
    }

    Banner.printBranding(System.out, Ansi.AUTO);
    System.out.println(
        Ansi.AUTO.string(
            "  @|green \u2713|@ Updated spec " + updated.id() + " to " + updated.status()));
  }
}
