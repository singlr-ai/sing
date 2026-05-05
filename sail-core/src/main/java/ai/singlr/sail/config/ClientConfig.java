/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.config;

import ai.singlr.sail.engine.SailPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Model for {@code ~/.sail/config.yaml} — client-side configuration on the engineer's Mac that
 * specifies how to reach the remote host server. When this file exists and the host config is
 * absent, the CLI operates in client mode: forwarding commands to the host via SSH.
 *
 * <p>Only {@code host} is required — it can be an IP, hostname, or an SSH config alias (e.g. {@code
 * kubera-server}). SSH resolves the user, key, and proxy settings from {@code ~/.ssh/config}.
 *
 * @param host the remote host — IP, hostname, or SSH config alias
 */
public record ClientConfig(String host) {

  public static ClientConfig fromMap(Map<String, Object> map) {
    var host = (String) map.get("host");
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException(
          "client config 'host' is required."
              + "\n  Set the host in ~/.sail/config.yaml: host: <server-ip-or-ssh-alias>");
    }
    return new ClientConfig(host);
  }

  public Map<String, Object> toMap() {
    var map = new LinkedHashMap<String, Object>();
    map.put("host", host);
    return map;
  }

  /** Returns true if the client config file exists at {@code ~/.sail/config.yaml}. */
  public static boolean exists() {
    return Files.exists(SailPaths.clientConfigPath());
  }

  /** Loads the client config from {@code ~/.sail/config.yaml}. */
  public static ClientConfig load() throws IOException {
    var path = SailPaths.clientConfigPath();
    if (!Files.exists(path)) {
      throw new IOException(
          "Client config not found: "
              + path
              + "\n  Create it with: sail init <server-ip-or-ssh-alias>");
    }
    return fromMap(YamlUtil.parseFile(path));
  }

  /** Loads the client config from a specific path (for testing). */
  public static ClientConfig load(Path path) throws IOException {
    return fromMap(YamlUtil.parseFile(path));
  }
}
