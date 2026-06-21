/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.commands;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FdeCommandTest {

  @Test
  void rosterEditOnNodeMessageNamesTheActionAndPointsAtMain() {
    var message = FdeCommand.rosterEditOnNodeMessage("Adding an FDE");
    assertTrue(message.contains("Adding an FDE"));
    assertTrue(message.contains("main devbox"));
    assertTrue(message.contains("Run this on main"));
    assertTrue(message.contains("overwritten on the next sync"));
  }
}
