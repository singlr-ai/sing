/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import static org.junit.jupiter.api.Assertions.*;

import ai.singlr.sing.Sing;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class ApiCommandTest {

  @Test
  void apiCommandIsRegistered() {
    var command = new CommandLine(new Sing());
    var output = new StringWriter();
    command.setOut(new PrintWriter(output));

    var exitCode = command.execute("--help");

    assertEquals(0, exitCode);
    assertTrue(output.toString().contains("api"));
  }

  @Test
  void apiHelpShowsSecurityOptions() {
    var command = new CommandLine(new Sing());
    var output = new StringWriter();
    command.setOut(new PrintWriter(output));

    var exitCode = command.execute("api", "--help");

    assertEquals(0, exitCode);
    assertTrue(output.toString().contains("--token"));
    assertTrue(output.toString().contains("--token-file"));
  }
}
