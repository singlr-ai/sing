/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.api;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class ServerConnectionConfigTest {

  @Test
  void flagsOverrideEverything() throws IOException {
    var config = ServerConnectionConfig.resolve("http://custom:9090", "my-token");
    assertEquals("http://custom:9090", config.serverUrl());
    assertEquals("my-token", config.token());
  }

  @Test
  void missingTokenWithoutConfigThrows() {
    assertThrows(
        IOException.class, () -> ServerConnectionConfig.resolve("http://localhost:7070", null));
  }

  @Test
  void defaultUrlIsLocalhostWhenNotProvided() throws IOException {
    var config = ServerConnectionConfig.resolve(null, "some-token");
    assertEquals("http://localhost:7070", config.serverUrl());
  }
}
