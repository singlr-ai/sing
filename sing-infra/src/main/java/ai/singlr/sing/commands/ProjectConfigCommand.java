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
    name = "config",
    description = "Show project configuration and live container status.",
    mixinStandardHelpOptions = true)
public final class ProjectConfigCommand implements Runnable {

  @Parameters(index = "0", description = "Project name.")
  private String name;

  @Option(
      names = {"-f", "--file"},
      description = "Path to sing.yaml project descriptor.",
      defaultValue = "sing.yaml")
  private String file;

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

    var singYamlPath = SingPaths.resolveSingYaml(name, file);
    if (!Files.exists(singYamlPath)) {
      throw new IllegalStateException(
          "Project descriptor not found: "
              + singYamlPath.toAbsolutePath()
              + "\n  Create a sing.yaml in the current directory, or specify one with --file.");
    }
    var config = SingYaml.fromMap(YamlUtil.parseFile(singYamlPath));

    var shell = new ShellExecutor(false);
    var mgr = new ContainerManager(shell);
    var state = mgr.queryState(name);

    if (json) {
      printJson(config, state);
      return;
    }

    Banner.printBranding(System.out, Ansi.AUTO);
    Banner.printProjectConfig(config, state, System.out, Ansi.AUTO);
  }

  @SuppressWarnings("unchecked")
  private static void printJson(SingYaml config, ContainerState state) {
    var map = new LinkedHashMap<String, Object>();
    map.put("name", config.name());
    map.put("description", config.description());
    map.put("image", config.image());

    if (config.resources() != null) {
      var res = new LinkedHashMap<String, Object>();
      res.put("cpu", config.resources().cpu());
      res.put("memory", config.resources().memory());
      res.put("disk", config.resources().disk());
      map.put("resources", res);
    }

    var statusStr =
        switch (state) {
          case ContainerState.Running ignored -> "running";
          case ContainerState.Stopped ignored -> "stopped";
          case ContainerState.NotCreated ignored -> "not_created";
          case ContainerState.Error ignored -> "error";
        };
    map.put("container_status", statusStr);
    if (state instanceof ContainerState.Running r && r.ipv4() != null) {
      map.put("container_ip", r.ipv4());
    }

    if (config.runtimes() != null) {
      var rt = new LinkedHashMap<String, Object>();
      if (config.runtimes().jdk() > 0) rt.put("jdk", config.runtimes().jdk());
      if (config.runtimes().node() != null) rt.put("node", config.runtimes().node());
      if (!rt.isEmpty()) map.put("runtimes", rt);
    }

    if (config.services() != null && !config.services().isEmpty()) {
      var svcs = new LinkedHashMap<String, Object>();
      for (var entry : config.services().entrySet()) {
        var svc = new LinkedHashMap<String, Object>();
        svc.put("image", entry.getValue().image());
        svc.put("ports", entry.getValue().ports());
        svcs.put(entry.getKey(), svc);
      }
      map.put("services", svcs);
    }

    if (config.agent() != null) {
      var agent = new LinkedHashMap<String, Object>();
      agent.put("type", config.agent().type());
      agent.put("auto_snapshot", config.agent().autoSnapshot());
      agent.put("auto_branch", config.agent().autoBranch());
      if (config.agent().install() != null) agent.put("install", config.agent().install());
      map.put("agent", agent);
    }

    if (config.ssh() != null) {
      map.put("ssh_user", config.ssh().user());
    }

    System.out.println(YamlUtil.dumpJson(map));
  }
}
