/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SingVersionTest {

  @Test
  void versionIsNotDev() {
    assertNotEquals("dev", SingVersion.version());
  }

  @Test
  void versionMatchesSemverFormat() {
    var version = SingVersion.version();
    assertTrue(version.matches("\\d+\\.\\d+\\.\\d+"), "Expected X.Y.Z format, got: " + version);
  }

  @Test
  void versionProviderReturnsSingPrefix() {
    var provider = new SingVersion();
    var lines = provider.getVersion();
    assertEquals(1, lines.length);
    assertTrue(lines[0].startsWith("sing "), "Expected 'sing X.Y.Z', got: " + lines[0]);
  }
}
