/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class AgentWatchCommandTest {

  @Test
  void helpTextIncludes() {
    var cmd = new CommandLine(new AgentWatchCommand());
    var usage = cmd.getUsageMessage();

    assertTrue(usage.contains("watch"));
    assertTrue(usage.contains("guardrails"));
    assertTrue(usage.contains("--interval"));
    assertTrue(usage.contains("--dry-run"));
    assertTrue(usage.contains("--json"));
  }

  @Test
  void requiresProjectName() {
    var cmd = new CommandLine(new AgentWatchCommand());
    var exitCode = cmd.execute();

    assertNotEquals(0, exitCode);
  }
}
