/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import ai.singlr.sing.config.ClientConfig;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Determines whether the CLI is running on the host server (with Incus) or on an engineer's client
 * machine (Mac). Host mode executes commands locally; client mode forwards commands to the host via
 * SSH.
 */
public sealed interface RuntimeMode {

  /** Running on the host server — commands execute locally via {@code incus exec}. */
  record Host() implements RuntimeMode {}

  /** Running on a client machine — commands are forwarded to the host via SSH. */
  record Client(ClientConfig config) implements RuntimeMode {}

  /**
   * Detects the runtime mode from standard file locations. Checks host config first (both new
   * {@code ~/.sing/host.yaml} and legacy {@code /etc/sing/host.yaml}) so a host that also has
   * client config still runs in host mode.
   */
  static RuntimeMode detect() {
    return detect(
        SingPaths.hostConfigPath(), SingPaths.legacyHostConfigPath(), SingPaths.clientConfigPath());
  }

  /**
   * Detects runtime mode from explicit paths. Package-private for testability.
   *
   * @param hostConfigPath path to the host config (e.g. ~/.sing/host.yaml)
   * @param legacyHostConfigPath legacy host config (e.g. /etc/sing/host.yaml)
   * @param clientConfigPath path to the client config (e.g. ~/.sing/config.yaml)
   */
  static RuntimeMode detect(Path hostConfigPath, Path legacyHostConfigPath, Path clientConfigPath) {
    if (Files.exists(hostConfigPath) || Files.exists(legacyHostConfigPath)) {
      return new Host();
    }
    if (Files.exists(clientConfigPath)) {
      try {
        var config = ClientConfig.load(clientConfigPath);
        return new Client(config);
      } catch (Exception ignored) {
        return new Host();
      }
    }
    return new Host();
  }
}
