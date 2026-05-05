/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.commands;

import ai.singlr.sail.config.SailYaml;
import ai.singlr.sail.config.Spec;
import ai.singlr.sail.config.SpecDirectory;
import ai.singlr.sail.config.YamlUtil;
import ai.singlr.sail.engine.Banner;
import ai.singlr.sail.engine.ContainerManager;
import ai.singlr.sail.engine.ContainerState;
import ai.singlr.sail.engine.NameValidator;
import ai.singlr.sail.engine.SailPaths;
import ai.singlr.sail.engine.ShellExecutor;
import ai.singlr.sail.engine.SpecWorkspace;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Lists specs from project per-spec metadata as a kanban board grouped by status. Reads the index
 * from inside the Incus container via {@code incus exec}.
 */
@Command(
    name = "list",
    description = "List specs as a kanban board.",
    mixinStandardHelpOptions = true)
public final class SpecListCommand implements Runnable {

  @Parameters(index = "0", description = "Project name.")
  private String name;

  @Option(
      names = {"-f", "--file"},
      description = "Path to sail.yaml project descriptor.",
      defaultValue = "sail.yaml")
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

    var shell = new ShellExecutor(false);
    var mgr = new ContainerManager(shell);

    var state = mgr.queryState(name);
    switch (state) {
      case ContainerState.Running ignored -> {}
      case ContainerState.Stopped ignored ->
          throw new IllegalStateException(
              "Project '" + name + "' is stopped. Start it with: sail project start " + name);
      case ContainerState.NotCreated ignored ->
          throw new IllegalStateException(
              "Project '" + name + "' does not exist. Run 'sail project create' first.");
      case ContainerState.Error e ->
          throw new IllegalStateException("Container error: " + e.message());
    }

    var singYamlPath = SailPaths.resolveSailYaml(name, file);
    if (!Files.exists(singYamlPath)) {
      throw new IllegalStateException(
          "Project descriptor not found: " + singYamlPath.toAbsolutePath());
    }
    var config = SailYaml.fromMap(YamlUtil.parseFile(singYamlPath));

    if (config.agent() == null || config.agent().specsDir() == null) {
      throw new IllegalStateException(
          "No specs_dir configured in agent block. Add it to sail.yaml.");
    }

    var sshUser = config.sshUser();
    var specsDir = "/home/" + sshUser + "/workspace/" + config.agent().specsDir();
    var workspace = new SpecWorkspace(shell, name, specsDir);
    var specs = workspace.readSpecs();
    if (specs.isEmpty()) {
      if (json) {
        var map = new LinkedHashMap<String, Object>();
        map.put("name", name);
        map.put("specs", List.of());
        map.put("counts", SpecDirectory.statusCounts(List.of()));
        map.put("summary", SpecDirectory.summarize(List.of()).toMap());
        System.out.println(YamlUtil.dumpJson(map));
      } else {
        Banner.printBranding(System.out, Ansi.AUTO);
        System.out.println(Ansi.AUTO.string("  @|faint No specs found for " + name + ".|@"));
      }
      return;
    }
    var summary = SpecDirectory.summarize(specs);

    if (json) {
      var map = new LinkedHashMap<String, Object>();
      map.put("name", name);
      map.put("specs", specs.stream().map(spec -> toJsonSpec(specs, spec)).toList());
      map.put("counts", summary.counts());
      map.put("summary", summary.toMap());
      System.out.println(YamlUtil.dumpJson(map));
      return;
    }

    Banner.printBranding(System.out, Ansi.AUTO);
    var ansi = Ansi.AUTO;

    var counts = SpecDirectory.statusCounts(specs);
    System.out.println(
        ansi.string(
            "  @|bold Spec Board:|@ "
                + name
                + "  ("
                + counts.getOrDefault("pending", 0)
                + " pending, "
                + counts.getOrDefault("in_progress", 0)
                + " in progress, "
                + counts.getOrDefault("review", 0)
                + " review, "
                + counts.getOrDefault("done", 0)
                + " done)"));
    System.out.println();

    var doneIds =
        specs.stream()
            .filter(s -> "done".equals(s.status()))
            .map(Spec::id)
            .collect(Collectors.toSet());

    printColumn(specs, "pending", doneIds, ansi);
    printColumn(specs, "in_progress", doneIds, ansi);
    printColumn(specs, "review", doneIds, ansi);
    printColumn(specs, "done", doneIds, ansi);
  }

  private void printColumn(List<Spec> specs, String status, Set<String> doneIds, Ansi ansi) {
    var matching = specs.stream().filter(s -> status.equals(s.status())).toList();
    if (matching.isEmpty()) {
      return;
    }

    var label = formatStatusLabel(status);
    var color = statusColor(status);

    System.out.println(
        ansi.string("  @|bold," + color + " " + label + " (" + matching.size() + ")|@"));
    for (var spec : matching) {
      var blocked = !spec.dependsOn().isEmpty() && !doneIds.containsAll(spec.dependsOn());
      var prefix = blocked ? "  \u2717 " : "  \u2022 ";
      var suffix = blocked ? " @|faint [blocked]|@" : "";
      var deps =
          spec.dependsOn().isEmpty()
              ? ""
              : " @|faint \u2190 " + String.join(", ", spec.dependsOn()) + "|@";
      System.out.println(
          ansi.string(prefix + "@|bold " + spec.id() + "|@  " + spec.title() + deps + suffix));
    }
    System.out.println();
  }

  private static String formatStatusLabel(String status) {
    return switch (status) {
      case "pending" -> "Pending";
      case "in_progress" -> "In Progress";
      case "review" -> "Review";
      case "done" -> "Done";
      default -> status;
    };
  }

  private static String statusColor(String status) {
    return switch (status) {
      case "pending" -> "white";
      case "in_progress" -> "blue";
      case "review" -> "yellow";
      case "done" -> "green";
      default -> "white";
    };
  }

  private static Map<String, Object> toJsonSpec(List<Spec> specs, Spec spec) {
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
