/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import ai.singlr.sing.config.YamlUtil;
import ai.singlr.sing.engine.Banner;
import ai.singlr.sing.engine.ContainerExec;
import ai.singlr.sing.engine.ContainerManager;
import ai.singlr.sing.engine.ContainerState;
import ai.singlr.sing.engine.HostDetector;
import ai.singlr.sing.engine.NameValidator;
import ai.singlr.sing.engine.ResourceChecker;
import ai.singlr.sing.engine.ShellExecutor;
import java.util.LinkedHashMap;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "up", description = "Start a project container.", mixinStandardHelpOptions = true)
public final class UpCommand implements Runnable {

  @Parameters(index = "0", description = "Project name.")
  private String name;

  @Option(names = "--dry-run", description = "Print commands instead of executing them.")
  private boolean dryRun;

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
    var shell = new ShellExecutor(dryRun);
    var mgr = new ContainerManager(shell);
    var state = mgr.queryState(name);

    switch (state) {
      case ContainerState.Running r -> {
        if (json) {
          printJson("running", r.ipv4());
          return;
        }
        Banner.printBranding(System.out, Ansi.AUTO);
        System.out.println();
        Banner.printContainerStatus(name, r, System.out, Ansi.AUTO);
        Banner.printZedConnect(name, null, System.out, Ansi.AUTO);
        printTunnelHints(shell);
        printOvercommitHints(mgr);
      }
      case ContainerState.Stopped ignored -> {
        if (!json) {
          System.out.println(Ansi.AUTO.string("  @|bold Starting|@ " + name + "..."));
        }
        mgr.start(name);
        var newState = mgr.queryState(name);
        var ip = newState instanceof ContainerState.Running r ? r.ipv4() : null;
        if (json) {
          printJson("started", ip);
          return;
        }
        Banner.printBranding(System.out, Ansi.AUTO);
        System.out.println();
        Banner.printContainerStatus(name, new ContainerState.Running(ip), System.out, Ansi.AUTO);
        Banner.printZedConnect(name, null, System.out, Ansi.AUTO);
        printTunnelHints(shell);
        printOvercommitHints(mgr);
      }
      case ContainerState.NotCreated ignored ->
          throw new IllegalStateException(
              "Project '" + name + "' does not exist. Run 'sing project create' first.");
      case ContainerState.Error e ->
          throw new IllegalStateException("Container error: " + e.message());
    }
  }

  private void printJson(String status, String ip) {
    var map = new LinkedHashMap<String, Object>();
    map.put("name", name);
    map.put("status", status);
    map.put("ip", ip);
    System.out.println(YamlUtil.dumpJson(map));
  }

  private void printTunnelHints(ShellExecutor shell) {
    try {
      var ports = ContainerExec.queryServicePorts(shell, name);
      Banner.printSshTunnels(name, null, ports, System.out, Ansi.AUTO);
    } catch (Exception ignored) {
    }
  }

  private void printOvercommitHints(ContainerManager mgr) {
    try {
      var containers = mgr.listAll();
      var running =
          containers.stream().filter(c -> c.state() instanceof ContainerState.Running).toList();
      var others = running.stream().filter(c -> !c.name().equals(name)).toList();
      Banner.printAlsoRunning(others, System.out, Ansi.AUTO);

      var hostInfo = new HostDetector().detect();
      var capacity = new ResourceChecker.HostCapacity(hostInfo.threads(), hostInfo.memoryMb());
      var overcommit = ResourceChecker.check(running, capacity);
      Banner.printOvercommitWarning(overcommit, System.out, Ansi.AUTO);
    } catch (Exception ignored) {
    }
  }
}
