/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConnectCommandTest {

  @Test
  void snippetUsesTheProjectsContainerUserNotAHardcodedDev() {
    var snippet = ConnectCommand.connectSnippet("10.0.0.1", "uday", "acme", "10.1.1.5", "engineer");

    assertTrue(snippet.contains("User engineer"), "container ssh user from the definition");
    assertTrue(snippet.contains("zed ssh://engineer@acme/home/engineer/workspace"));
    assertFalse(snippet.contains("dev@"), "no hardcoded dev user");
  }

  @Test
  void jsonReportsTheResolvedContainerUser() {
    var map = ConnectCommand.connectJson("acme", "10.0.0.1", "uday", "10.1.1.5", "engineer");
    assertEquals("engineer", map.get("container_user"));
    assertEquals("acme", map.get("project"));
  }
}
