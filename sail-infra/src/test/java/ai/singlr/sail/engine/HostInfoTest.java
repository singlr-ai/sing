/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HostInfoTest {

  @Test
  void hostnameIsNonBlank() {
    var name = HostInfo.hostname();
    assertNotNull(name);
    assertFalse(name.isBlank());
  }

  @Test
  void hostnameIsStable() {
    assertEquals(HostInfo.hostname(), HostInfo.hostname());
  }
}
