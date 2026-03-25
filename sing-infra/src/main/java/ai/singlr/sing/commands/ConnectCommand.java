/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import ai.singlr.sing.config.HostYaml;
import ai.singlr.sing.config.YamlUtil;
import ai.singlr.sing.engine.Banner;
import ai.singlr.sing.engine.ContainerManager;
import ai.singlr.sing.engine.ContainerState;
import ai.singlr.sing.engine.NameValidator;
import ai.singlr.sing.engine.ShellExecutor;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
    name = "connect",
    description = "Print SSH config snippet for connecting to a project container from your Mac.",
    mixinStandardHelpOptions = true)
public final class ConnectCommand implements Runnable {

  @Parameters(index = "0", description = "Project name.")
  private String name;

  @Option(
      names = "--server-ip",
      description = "Override server IP (instead of reading from host.yaml).")
  private String serverIp;

  @Option(names = "--server-user", description = "Server SSH user (default: current system user).")
  private String serverUser;

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

    var hostYamlPath = Path.of(HostYaml.DEFAULT_PATH);
    var resolvedServerIp = serverIp;
    if (resolvedServerIp == null) {
      var hostMap = YamlUtil.parseFile(hostYamlPath);
      var hostYaml = HostYaml.fromMap(hostMap);
      resolvedServerIp = hostYaml.serverIp();
    }
    if (resolvedServerIp == null) {
      throw new IllegalStateException(
          "Server IP not configured. Fix with one of:\n"
              + "  - sudo sing host config set server-ip <your-server-ip>\n"
              + "  - Or pass: sing connect "
              + name
              + " --server-ip <your-server-ip>");
    }

    var state = mgr.queryState(name);
    var containerIp =
        switch (state) {
          case ContainerState.Running r -> r.ipv4();
          case ContainerState.Stopped ignored ->
              throw new IllegalStateException(
                  "Project '" + name + "' is stopped. Start it with: sing up " + name);
          case ContainerState.NotCreated ignored ->
              throw new IllegalStateException(
                  "Project '"
                      + name
                      + "' does not exist. Create it with: sing project create <name>");
          case ContainerState.Error e ->
              throw new IllegalStateException("Container error: " + e.message());
        };

    if (containerIp == null) {
      throw new IllegalStateException(
          "Project '"
              + name
              + "' is running but has no IP address yet. Wait a moment and try again.");
    }

    var resolvedServerUser =
        Objects.requireNonNullElse(serverUser, System.getProperty("user.name"));

    if (json) {
      var map = new LinkedHashMap<String, Object>();
      map.put("project", name);
      map.put("server_ip", resolvedServerIp);
      map.put("server_user", resolvedServerUser);
      map.put("container_ip", containerIp);
      map.put("container_user", "dev");
      System.out.println(YamlUtil.dumpJson(map));
      return;
    }

    var snippet =
        """
          # Add to ~/.ssh/config on your Mac:

          Host singular-server
              HostName %s
              User %s
              IdentityFile ~/.ssh/id_ed25519

          Host %s
              HostName %s
              User dev
              ProxyJump singular-server
              IdentityFile ~/.ssh/id_ed25519

          # Then connect:
          #   ssh %s
          #   zed ssh://dev@%s/home/dev/workspace
          #
          # Agent auth (port forwarding for subscription login):
          #   ssh -N -L 3000:localhost:3000 %s"""
            .formatted(resolvedServerIp, resolvedServerUser, name, containerIp, name, name, name);
    System.out.println(snippet);
  }
}
