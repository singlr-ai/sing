/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import static org.junit.jupiter.api.Assertions.*;

import ai.singlr.sing.Sing;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class SnapsPruneCommandTest {

  @Test
  void commandRegisteredInSing() {
    var cmd = new CommandLine(new Sing());
    var sw = new StringWriter();
    cmd.setOut(new PrintWriter(sw));

    cmd.execute("--help");

    assertTrue(sw.toString().contains("snaps-prune"));
  }

  @Test
  void helpShowsDescription() {
    var cmd = new CommandLine(new Sing());
    var sw = new StringWriter();
    cmd.setOut(new PrintWriter(sw));

    var exitCode = cmd.execute("snaps-prune", "--help");

    assertEquals(0, exitCode);
    assertTrue(sw.toString().contains("older than"));
  }

  @Test
  void helpShowsOlderThanFlag() {
    var cmd = new CommandLine(new Sing());
    var sw = new StringWriter();
    cmd.setOut(new PrintWriter(sw));

    cmd.execute("snaps-prune", "--help");

    assertTrue(sw.toString().contains("--older-than"));
    assertTrue(sw.toString().contains("--dry-run"));
    assertTrue(sw.toString().contains("--json"));
  }

  @Test
  void failsWithoutOlderThanFlag() {
    var cmd = new CommandLine(new Sing());
    var sw = new StringWriter();
    cmd.setErr(new PrintWriter(sw));

    var exitCode = cmd.execute("snaps-prune");

    assertNotEquals(0, exitCode);
  }

  @Test
  void parseAgeDays() {
    var duration = SnapsPruneCommand.parseAge("7d");

    assertEquals(Duration.ofDays(7), duration);
  }

  @Test
  void parseAgeHours() {
    var duration = SnapsPruneCommand.parseAge("24h");

    assertEquals(Duration.ofHours(24), duration);
  }

  @Test
  void parseAgeMinutes() {
    var duration = SnapsPruneCommand.parseAge("30m");

    assertEquals(Duration.ofMinutes(30), duration);
  }

  @Test
  void parseAgeRejectsInvalidFormat() {
    assertThrows(IllegalArgumentException.class, () -> SnapsPruneCommand.parseAge("7x"));
  }

  @Test
  void parseAgeRejectsEmpty() {
    assertThrows(IllegalArgumentException.class, () -> SnapsPruneCommand.parseAge(""));
  }

  @Test
  void parseAgeTrimsWhitespace() {
    var duration = SnapsPruneCommand.parseAge("  3d  ");

    assertEquals(Duration.ofDays(3), duration);
  }

  @Test
  void projectNameIsOptional() {
    var cmd = new CommandLine(new Sing());
    var sw = new StringWriter();
    cmd.setOut(new PrintWriter(sw));

    cmd.execute("snaps-prune", "--help");

    assertTrue(sw.toString().contains("[<name>]") || sw.toString().contains("Project name"));
  }
}
