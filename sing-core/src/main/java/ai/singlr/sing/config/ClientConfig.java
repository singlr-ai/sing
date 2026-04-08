/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.config;

import ai.singlr.sing.engine.SingPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Model for {@code ~/.sing/config.yaml} — client-side configuration on the engineer's Mac that
 * specifies how to reach the remote host server. When this file exists and the host config is
 * absent, the CLI operates in client mode: forwarding commands to the host via SSH.
 *
 * @param host the remote host IP or hostname
 * @param user the SSH user on the remote host (typically "root")
 * @param sshKey the path to the SSH private key (tilde-expanded)
 */
public record ClientConfig(String host, String user, String sshKey) {

  public static ClientConfig fromMap(Map<String, Object> map) {
    var host = (String) map.get("host");
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException(
          "client config 'host' is required."
              + "\n  Set the host IP in ~/.sing/config.yaml: host: <server-ip>");
    }
    var user = Objects.requireNonNullElse((String) map.get("user"), "root");
    var key = SingPaths.expandHome((String) map.get("key"));
    return new ClientConfig(host, user, key);
  }

  public Map<String, Object> toMap() {
    var map = new LinkedHashMap<String, Object>();
    map.put("host", host);
    map.put("user", user);
    if (sshKey != null) {
      map.put("key", sshKey);
    }
    return map;
  }

  /** Returns true if the client config file exists at {@code ~/.sing/config.yaml}. */
  public static boolean exists() {
    return Files.exists(SingPaths.clientConfigPath());
  }

  /** Loads the client config from {@code ~/.sing/config.yaml}. */
  public static ClientConfig load() throws IOException {
    var path = SingPaths.clientConfigPath();
    if (!Files.exists(path)) {
      throw new IOException(
          "Client config not found: "
              + path
              + "\n  Create ~/.sing/config.yaml with: host, user, key");
    }
    return fromMap(YamlUtil.parseFile(path));
  }

  /** Loads the client config from a specific path (for testing). */
  public static ClientConfig load(Path path) throws IOException {
    return fromMap(YamlUtil.parseFile(path));
  }
}
