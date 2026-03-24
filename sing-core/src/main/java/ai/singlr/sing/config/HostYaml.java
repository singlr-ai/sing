/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Model for {@code /etc/sing/host.yaml} — server-side state written by {@code sing host init} and
 * read by all other commands.
 */
public record HostYaml(
    String storageBackend,
    String pool,
    String poolDisk,
    String bridge,
    String baseProfile,
    String image,
    String incusVersion,
    String serverIp,
    String initializedAt) {
  public static final String DEFAULT_PATH = "/etc/sing/host.yaml";
  public static final String DEFAULT_POOL = "devpool";
  public static final String DEFAULT_BRIDGE = "incusbr0";
  public static final String DEFAULT_PROFILE = "singlr-base";
  public static final String DEFAULT_IMAGE = "ubuntu/24.04";
  public static final String BACKEND_DIR = "dir";
  public static final String BACKEND_ZFS = "zfs";

  public boolean isDir() {
    return BACKEND_DIR.equals(storageBackend);
  }

  public boolean isZfs() {
    return BACKEND_ZFS.equals(storageBackend);
  }

  public static HostYaml fromMap(Map<String, Object> map) {
    var backend = Objects.requireNonNullElse((String) map.get("storage_backend"), BACKEND_ZFS);
    return new HostYaml(
        backend,
        (String) map.get("pool"),
        (String) map.get("pool_disk"),
        (String) map.get("bridge"),
        (String) map.get("base_profile"),
        (String) map.get("image"),
        (String) map.get("incus_version"),
        (String) map.get("server_ip"),
        (String) map.get("initialized_at"));
  }

  public Map<String, Object> toMap() {
    var map = new LinkedHashMap<String, Object>();
    map.put("storage_backend", storageBackend);
    map.put("pool", pool);
    map.put("pool_disk", poolDisk);
    map.put("bridge", bridge);
    map.put("base_profile", baseProfile);
    map.put("image", image);
    map.put("incus_version", incusVersion);
    map.put("server_ip", serverIp);
    map.put("initialized_at", initializedAt);
    return map;
  }
}
