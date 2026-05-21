/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SailYamlNotificationsResolverTest {

  @Test
  void unknownProjectResolvesToNull() {
    var resolver = new SailYamlNotificationsResolver();
    assertNull(resolver.resolve("this-project-definitely-does-not-exist-anywhere"));
  }
}
