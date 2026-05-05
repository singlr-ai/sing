/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HostYamlTest {

  @Test
  void parsesHostYaml() throws Exception {
    var yaml =
        """
            storage_backend: zfs
            pool: devpool
            pool_disk: /dev/sdb
            bridge: incusbr0
            base_profile: singlr-base
            image: ubuntu/24.04
            incus_version: "6.21"
            initialized_at: "2026-02-18T01:00:00Z"
            """;

    var host = HostYaml.fromMap(YamlUtil.parseMap(yaml));

    assertEquals("zfs", host.storageBackend());
    assertTrue(host.isZfs());
    assertFalse(host.isDir());
    assertEquals("devpool", host.pool());
    assertEquals("/dev/sdb", host.poolDisk());
    assertEquals("incusbr0", host.bridge());
    assertEquals("singlr-base", host.baseProfile());
    assertEquals("ubuntu/24.04", host.image());
    assertEquals("6.21", host.incusVersion());
    assertEquals("2026-02-18T01:00:00Z", host.initializedAt());
  }

  @Test
  void parsesHostYamlWithDirBackend() throws Exception {
    var yaml =
        """
            storage_backend: dir
            pool: devpool
            bridge: incusbr0
            base_profile: singlr-base
            image: ubuntu/24.04
            incus_version: "6.21"
            initialized_at: "2026-02-18T01:00:00Z"
            """;

    var host = HostYaml.fromMap(YamlUtil.parseMap(yaml));

    assertEquals("dir", host.storageBackend());
    assertTrue(host.isDir());
    assertFalse(host.isZfs());
    assertNull(host.poolDisk());
  }

  @Test
  void missingStorageBackendDefaultsToZfs() throws Exception {
    var yaml =
        """
            pool: devpool
            pool_disk: /dev/sdb
            bridge: incusbr0
            base_profile: singlr-base
            image: ubuntu/24.04
            incus_version: "6.21"
            initialized_at: "2026-02-18T01:00:00Z"
            """;

    var host = HostYaml.fromMap(YamlUtil.parseMap(yaml));

    assertEquals("zfs", host.storageBackend());
    assertTrue(host.isZfs());
  }

  @Test
  void serializesToYaml() throws Exception {
    var host =
        new HostYaml(
            "zfs",
            "devpool",
            "/dev/sdb",
            "incusbr0",
            "singlr-base",
            "ubuntu/24.04",
            "6.21",
            null,
            "2026-02-18T01:00:00Z");

    var yaml = YamlUtil.dumpToString(host.toMap());

    assertTrue(yaml.contains("storage_backend: zfs") || yaml.contains("storage_backend: \"zfs\""));
    assertTrue(yaml.contains("pool: devpool") || yaml.contains("pool: \"devpool\""));
    assertTrue(yaml.contains("pool_disk:"));
    assertTrue(yaml.contains("base_profile:"));
  }
}
