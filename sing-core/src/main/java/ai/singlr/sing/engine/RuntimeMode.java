/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
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
   * Detects the runtime mode. Host if {@code ~/.sing/host.yaml} exists, client if {@code
   * ~/.sing/config.yaml} exists, host otherwise (backward-compatible default).
   */
  static RuntimeMode detect() {
    return detect(SingPaths.hostConfigPath(), SingPaths.clientConfigPath());
  }

  /** Package-private overload for testability. */
  static RuntimeMode detect(Path hostConfigPath, Path clientConfigPath) {
    if (Files.exists(hostConfigPath)) {
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
