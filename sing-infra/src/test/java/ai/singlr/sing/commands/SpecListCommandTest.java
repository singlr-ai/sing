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

class SpecListCommandTest {

  @Test
  void specCommandRegisteredInSing() {
    var cmd = new CommandLine(new Sing());
    var sw = new StringWriter();
    cmd.setOut(new PrintWriter(sw));

    cmd.execute("--help");

    assertTrue(sw.toString().contains("spec"));
  }

  @Test
  void specListHelpShowsDescription() {
    var cmd = new CommandLine(new Sing());
    var sw = new StringWriter();
    cmd.setOut(new PrintWriter(sw));

    var exitCode = cmd.execute("spec", "list", "--help");

    assertEquals(0, exitCode);
    assertTrue(sw.toString().contains("kanban"));
  }

  @Test
  void specListRequiresProjectName() {
    var cmd = new CommandLine(new Sing());
    var sw = new StringWriter();
    cmd.setErr(new PrintWriter(sw));

    var exitCode = cmd.execute("spec", "list");

    assertNotEquals(0, exitCode);
  }

  @Test
  void specListAcceptsJsonFlag() {
    var cmd = new CommandLine(new Sing());
    var sw = new StringWriter();
    cmd.setOut(new PrintWriter(sw));

    var exitCode = cmd.execute("spec", "list", "--help");

    assertEquals(0, exitCode);
    assertTrue(sw.toString().contains("--json"));
  }

  @Test
  void specListAcceptsFileFlag() {
    var cmd = new CommandLine(new Sing());
    var sw = new StringWriter();
    cmd.setOut(new PrintWriter(sw));

    var exitCode = cmd.execute("spec", "list", "--help");

    assertEquals(0, exitCode);
    assertTrue(sw.toString().contains("--file"));
  }
}
