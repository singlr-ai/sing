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
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "show",
    description = "Show a spec's metadata and markdown.",
    mixinStandardHelpOptions = true)
public final class SpecShowCommand implements Runnable {

  @Parameters(index = "0", description = "Project name.")
  private String name;

  @Parameters(index = "1", description = "Spec id.")
  private String specId;

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

    var specsDir = "/home/" + config.sshUser() + "/workspace/" + config.agent().specsDir();
    var workspace = new SpecWorkspace(shell, name, specsDir);
    var specs = workspace.readIndex();
    var spec = SpecDirectory.findById(specs, specId);
    if (spec == null) {
      throw new IllegalArgumentException("Spec '" + specId + "' not found in index.yaml");
    }

    var content = workspace.readSpecBody(specId);
    if (json) {
      var map = new LinkedHashMap<String, Object>();
      map.put("name", name);
      map.put("spec", toJsonSpec(specs, spec));
      map.put("spec_path", workspace.specMarkdownPath(specId));
      map.put("content_available", content != null);
      if (content != null) {
        map.put("content", content);
      }
      System.out.println(YamlUtil.dumpJson(map));
      return;
    }

    Banner.printBranding(System.out, Ansi.AUTO);
    System.out.println(Ansi.AUTO.string("  @|bold Spec:|@ " + spec.id()));
    System.out.println(Ansi.AUTO.string("  @|bold Title:|@ " + spec.title()));
    System.out.println(Ansi.AUTO.string("  @|bold Status:|@ " + spec.status()));
    if (spec.assignee() != null) {
      System.out.println(Ansi.AUTO.string("  @|bold Assignee:|@ " + spec.assignee()));
    }
    if (!spec.dependsOn().isEmpty()) {
      System.out.println(
          Ansi.AUTO.string("  @|bold Depends On:|@ " + String.join(", ", spec.dependsOn())));
    }
    System.out.println(Ansi.AUTO.string("  @|bold Path:|@ " + workspace.specMarkdownPath(specId)));
    System.out.println();
    if (content != null && !content.isBlank()) {
      System.out.println(content);
    } else {
      System.out.println(Ansi.AUTO.string("  @|faint spec.md not found.|@"));
    }
  }

  private static LinkedHashMap<String, Object> toJsonSpec(
      List<ai.singlr.sing.config.Spec> specs, ai.singlr.sing.config.Spec spec) {
    var map = new LinkedHashMap<String, Object>(spec.toMap());
    var ready = SpecDirectory.isReady(specs, spec);
    var blocked = SpecDirectory.isBlocked(specs, spec);
    map.put("ready", ready);
    map.put("blocked", blocked);
    if (blocked) {
      map.put("unmet_dependencies", SpecDirectory.unmetDependencies(specs, spec));
    }
    return map;
  }
}
