/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import ai.singlr.sing.config.YamlUtil;
import ai.singlr.sing.engine.Banner;
import ai.singlr.sing.engine.ContainerManager;
import ai.singlr.sing.engine.ContainerState;
import ai.singlr.sing.engine.ShellExecutor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
    name = "list",
    description = "List all project containers and their status.",
    mixinStandardHelpOptions = true)
public final class ProjectListCommand implements Runnable {

  @Option(names = "--json", description = "Output in JSON format.")
  private boolean json;

  @Spec private CommandSpec spec;

  @Override
  public void run() {
    CliCommand.run(spec, this::execute);
  }

  private void execute() throws Exception {
    var shell = new ShellExecutor(false);
    var mgr = new ContainerManager(shell);
    var containers = mgr.listAll();

    if (json) {
      printJson(containers);
      return;
    }

    Banner.printBranding(System.out, Ansi.AUTO);

    if (containers.isEmpty()) {
      System.out.println();
      System.out.println(
          Ansi.AUTO.string(
              "  @|faint No projects found.|@ Create one with: @|bold sing project create|@"));
      System.out.println();
      return;
    }

    Banner.printProjectTable(containers, System.out, Ansi.AUTO);
  }

  private void printJson(List<ContainerManager.ContainerInfo> containers) {
    var list = new ArrayList<java.util.Map<String, Object>>();
    for (var c : containers) {
      var map = new LinkedHashMap<String, Object>();
      map.put("name", c.name());
      map.put(
          "status",
          switch (c.state()) {
            case ContainerState.Running ignored -> "running";
            case ContainerState.Stopped ignored -> "stopped";
            case ContainerState.NotCreated ignored -> "not_created";
            case ContainerState.Error ignored -> "error";
          });
      if (c.state() instanceof ContainerState.Running r && r.ipv4() != null) {
        map.put("ip", r.ipv4());
      }
      list.add(map);
    }
    System.out.println(YamlUtil.dumpJson(list));
  }
}
