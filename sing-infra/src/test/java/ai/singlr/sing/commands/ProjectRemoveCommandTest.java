/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class ProjectRemoveCommandTest {

  @Test
  void helpTextShowsServiceSubcommand() {
    var cmd = new CommandLine(new ProjectRemoveCommand());
    var usage = cmd.getUsageMessage();

    assertTrue(usage.contains("service"));
    assertTrue(usage.contains("Remove"));
  }

  @Test
  void showsUsageWhenInvokedWithoutSubcommand() {
    var cmd = new CommandLine(new ProjectRemoveCommand());
    var exitCode = cmd.execute();

    assertEquals(0, exitCode);
  }
}
