/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import ai.singlr.sing.config.YamlUtil;
import ai.singlr.sing.engine.Banner;
import ai.singlr.sing.engine.ContainerManager;
import ai.singlr.sing.engine.ContainerState;
import ai.singlr.sing.engine.NameValidator;
import ai.singlr.sing.engine.ShellExecutor;
import ai.singlr.sing.engine.SnapshotManager;
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
    name = "list",
    description = "List snapshots for a project container.",
    mixinStandardHelpOptions = true)
public final class SnapsCommand implements Runnable {

  @Parameters(index = "0", description = "Project name.")
  private String name;

  @Option(names = "--json", description = "Output in JSON format.")
  private boolean json;

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
    var shell = new ShellExecutor(false);
    var mgr = new ContainerManager(shell);
    var snapMgr = new SnapshotManager(shell);

    var state = mgr.queryState(name);
    switch (state) {
      case ContainerState.Running ignored -> {}
      case ContainerState.Stopped ignored -> {}
      case ContainerState.NotCreated ignored ->
          throw new IllegalStateException(
              "Project '" + name + "' does not exist. Run 'sing project create' first.");
      case ContainerState.Error e ->
          throw new IllegalStateException("Container error: " + e.message());
    }

    var snapshots = snapMgr.list(name);

    if (json) {
      printJson(snapshots);
      return;
    }

    Banner.printBranding(System.out, Ansi.AUTO);

    if (snapshots.isEmpty()) {
      System.out.println();
      System.out.println(
          Ansi.AUTO.string(
              "  @|faint No snapshots for "
                  + name
                  + ". Create one with:|@ @|bold sing project snapshot create "
                  + name
                  + "|@"));
      return;
    }

    Banner.printSnapshotTable(name, snapshots, System.out, Ansi.AUTO);
  }

  private static void printJson(List<SnapshotManager.SnapshotInfo> snapshots) {
    var list =
        snapshots.stream()
            .map(
                s -> {
                  var map = new LinkedHashMap<String, Object>();
                  map.put("name", s.name());
                  map.put("created_at", s.createdAt());
                  return (Object) map;
                })
            .toList();
    System.out.println(YamlUtil.dumpJson(list));
  }
}
