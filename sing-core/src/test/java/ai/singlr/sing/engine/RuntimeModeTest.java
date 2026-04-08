/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeModeTest {

  @TempDir Path tempDir;

  private Path absent(String name) {
    return tempDir.resolve(name);
  }

  @Test
  void detectsHostWhenHostYamlExists() throws Exception {
    var hostPath = tempDir.resolve("host.yaml");
    Files.writeString(hostPath, "storage_backend: dir\n");

    var mode = RuntimeMode.detect(hostPath, absent("legacy.yaml"), absent("config.yaml"));

    assertInstanceOf(RuntimeMode.Host.class, mode);
  }

  @Test
  void detectsHostWhenLegacyHostYamlExists() throws Exception {
    var legacyPath = tempDir.resolve("legacy-host.yaml");
    Files.writeString(legacyPath, "storage_backend: dir\n");

    var mode = RuntimeMode.detect(absent("host.yaml"), legacyPath, absent("config.yaml"));

    assertInstanceOf(RuntimeMode.Host.class, mode);
  }

  @Test
  void detectsClientWhenOnlyClientConfigExists() throws Exception {
    var clientPath = tempDir.resolve("config.yaml");
    Files.writeString(clientPath, "host: 192.168.1.100\nuser: root\n");

    var mode = RuntimeMode.detect(absent("host.yaml"), absent("legacy.yaml"), clientPath);

    assertInstanceOf(RuntimeMode.Client.class, mode);
    var client = (RuntimeMode.Client) mode;
    assertEquals("192.168.1.100", client.config().host());
  }

  @Test
  void prefersHostWhenBothExist() throws Exception {
    var hostPath = tempDir.resolve("host.yaml");
    Files.writeString(hostPath, "storage_backend: dir\n");
    var clientPath = tempDir.resolve("config.yaml");
    Files.writeString(clientPath, "host: 10.0.0.1\n");

    var mode = RuntimeMode.detect(hostPath, absent("legacy.yaml"), clientPath);

    assertInstanceOf(RuntimeMode.Host.class, mode);
  }

  @Test
  void defaultsToHostWhenNeitherExists() {
    var mode =
        RuntimeMode.detect(absent("host.yaml"), absent("legacy.yaml"), absent("config.yaml"));

    assertInstanceOf(RuntimeMode.Host.class, mode);
  }

  @Test
  void defaultsToHostWhenClientConfigInvalid() throws Exception {
    var clientPath = tempDir.resolve("config.yaml");
    Files.writeString(clientPath, "user: root\n");

    var mode = RuntimeMode.detect(absent("host.yaml"), absent("legacy.yaml"), clientPath);

    assertInstanceOf(RuntimeMode.Host.class, mode);
  }

  @Test
  void clientConfigWithSshKey() throws Exception {
    var clientPath = tempDir.resolve("config.yaml");
    Files.writeString(
        clientPath, "host: 10.0.0.1\nuser: deploy\nkey: /home/deploy/.ssh/id_ed25519\n");

    var mode = RuntimeMode.detect(absent("host.yaml"), absent("legacy.yaml"), clientPath);

    assertInstanceOf(RuntimeMode.Client.class, mode);
    var client = (RuntimeMode.Client) mode;
    assertEquals("/home/deploy/.ssh/id_ed25519", client.config().sshKey());
  }
}
