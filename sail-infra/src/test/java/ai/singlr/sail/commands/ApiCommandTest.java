/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.commands;

import static org.junit.jupiter.api.Assertions.*;

import ai.singlr.sail.Sail;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class ApiCommandTest {

  @Test
  void apiCommandIsRegistered() {
    var command = new CommandLine(new Sail());
    var output = new StringWriter();
    command.setOut(new PrintWriter(output));

    var exitCode = command.execute("--help");

    assertEquals(0, exitCode);
    assertTrue(output.toString().contains("api"));
  }

  @Test
  void apiHelpShowsSecurityOptions() {
    var command = new CommandLine(new Sail());
    var output = new StringWriter();
    command.setOut(new PrintWriter(output));

    var exitCode = command.execute("api", "--help");

    assertEquals(0, exitCode);
    assertTrue(output.toString().contains("--token"));
    assertTrue(output.toString().contains("--token-file"));
  }

  @Test
  void loopbackApiBindIsAllowedByDefault() throws Exception {
    assertDoesNotThrow(() -> ApiCommand.requireSafeBindAddress("127.0.0.1", false));
    assertDoesNotThrow(() -> ApiCommand.requireSafeBindAddress("localhost", false));
  }

  @Test
  void nonLoopbackApiBindRequiresExplicitOptIn() {
    var error =
        assertThrows(
            IllegalArgumentException.class,
            () -> ApiCommand.requireSafeBindAddress("0.0.0.0", false));

    assertTrue(error.getMessage().contains("Refusing to bind"));
    assertDoesNotThrow(() -> ApiCommand.requireSafeBindAddress("0.0.0.0", true));
  }
}
