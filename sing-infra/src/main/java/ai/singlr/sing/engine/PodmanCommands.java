/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import ai.singlr.sing.config.SingYaml;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@code podman run} command lists from {@link SingYaml.Service} records. Pure utility — no
 * side effects, no shell execution. The returned lists are passed directly to {@link ShellExec}.
 */
public final class PodmanCommands {

  private PodmanCommands() {}

  /**
   * Builds a {@code podman run -d --restart=always} command for the given service.
   *
   * @param serviceName the container name (e.g. "postgres")
   * @param service the service definition from sing.yaml
   * @return an unmodifiable command list ready for {@link ShellExec#exec}
   */
  public static List<String> buildRunCommand(String serviceName, SingYaml.Service service) {
    var cmd = new ArrayList<String>();
    cmd.addAll(List.of("podman", "run", "-d", "--restart=always", "--name", serviceName));

    if (service.ports() != null) {
      for (var port : service.ports()) {
        cmd.add("-p");
        cmd.add(port + ":" + port);
      }
    }

    if (service.environment() != null) {
      for (var entry : service.environment().entrySet()) {
        cmd.add("-e");
        cmd.add(entry.getKey() + "=" + entry.getValue());
      }
    }

    if (service.volumes() != null) {
      for (var vol : service.volumes()) {
        cmd.add("-v");
        cmd.add(vol);
      }
    }

    cmd.add(service.image());

    if (service.command() != null && !service.command().isBlank()) {
      for (var arg : service.command().strip().split("\\s+")) {
        cmd.add(arg);
      }
    }

    return List.copyOf(cmd);
  }
}
