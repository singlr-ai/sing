/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientConfigTest {

  @TempDir Path tempDir;

  @Test
  void fromMapParsesAllFields() {
    var map =
        Map.<String, Object>of(
            "host", "192.168.1.100", "user", "deploy", "key", "/home/me/.ssh/id_ed25519");

    var config = ClientConfig.fromMap(map);

    assertEquals("192.168.1.100", config.host());
    assertEquals("deploy", config.user());
    assertEquals("/home/me/.ssh/id_ed25519", config.sshKey());
  }

  @Test
  void fromMapDefaultsUserToRoot() {
    var map = Map.<String, Object>of("host", "10.0.0.1");

    var config = ClientConfig.fromMap(map);

    assertEquals("root", config.user());
  }

  @Test
  void fromMapExpandsTildeInKey() {
    var map = Map.<String, Object>of("host", "10.0.0.1", "key", "~/.ssh/id_ed25519");

    var config = ClientConfig.fromMap(map);

    assertTrue(config.sshKey().startsWith("/"));
    assertTrue(config.sshKey().endsWith("/.ssh/id_ed25519"));
    assertFalse(config.sshKey().startsWith("~"));
  }

  @Test
  void fromMapThrowsWhenHostMissing() {
    var map = Map.<String, Object>of("user", "root");

    var ex = assertThrows(IllegalArgumentException.class, () -> ClientConfig.fromMap(map));

    assertTrue(ex.getMessage().contains("host"));
    assertTrue(ex.getMessage().contains("config.yaml"));
  }

  @Test
  void fromMapThrowsWhenHostBlank() {
    var map = Map.<String, Object>of("host", "   ");

    assertThrows(IllegalArgumentException.class, () -> ClientConfig.fromMap(map));
  }

  @Test
  void fromMapHandlesNullKey() {
    var map = new LinkedHashMap<String, Object>();
    map.put("host", "10.0.0.1");
    map.put("key", null);

    var config = ClientConfig.fromMap(map);

    assertNull(config.sshKey());
  }

  @Test
  void toMapRoundTrips() {
    var original = new ClientConfig("192.168.1.100", "deploy", "/home/me/.ssh/id_ed25519");

    var rebuilt = ClientConfig.fromMap(original.toMap());

    assertEquals(original.host(), rebuilt.host());
    assertEquals(original.user(), rebuilt.user());
    assertEquals(original.sshKey(), rebuilt.sshKey());
  }

  @Test
  void toMapOmitsNullKey() {
    var config = new ClientConfig("10.0.0.1", "root", null);

    var map = config.toMap();

    assertFalse(map.containsKey("key"));
  }

  @Test
  void loadFromFile() throws Exception {
    var yaml =
        """
        host: 192.168.1.50
        user: admin
        key: /home/admin/.ssh/id_rsa
        """;
    var file = tempDir.resolve("config.yaml");
    Files.writeString(file, yaml);

    var config = ClientConfig.load(file);

    assertEquals("192.168.1.50", config.host());
    assertEquals("admin", config.user());
    assertEquals("/home/admin/.ssh/id_rsa", config.sshKey());
  }

  @Test
  void loadThrowsWhenFileNotFound() {
    var missing = tempDir.resolve("nonexistent.yaml");

    assertThrows(IOException.class, () -> ClientConfig.load(missing));
  }
}
