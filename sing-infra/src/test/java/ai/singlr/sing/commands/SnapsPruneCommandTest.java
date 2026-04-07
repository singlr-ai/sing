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
import java.time.Instant;
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
  void helpShowsAllFlags() {
    var cmd = new CommandLine(new Sing());
    var sw = new StringWriter();
    cmd.setOut(new PrintWriter(sw));

    cmd.execute("snaps-prune", "--help");

    var help = sw.toString();
    assertTrue(help.contains("--older-than"));
    assertTrue(help.contains("--dry-run"));
    assertTrue(help.contains("--json"));
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
    assertEquals(Duration.ofDays(7), SnapsPruneCommand.parseAge("7d"));
  }

  @Test
  void parseAgeHours() {
    assertEquals(Duration.ofHours(24), SnapsPruneCommand.parseAge("24h"));
  }

  @Test
  void parseAgeMinutes() {
    assertEquals(Duration.ofMinutes(30), SnapsPruneCommand.parseAge("30m"));
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
    assertEquals(Duration.ofDays(3), SnapsPruneCommand.parseAge("  3d  "));
  }

  @Test
  void projectNameIsOptional() {
    var cmd = new CommandLine(new Sing());
    var sw = new StringWriter();
    cmd.setOut(new PrintWriter(sw));

    cmd.execute("snaps-prune", "--help");

    assertTrue(sw.toString().contains("[<name>]") || sw.toString().contains("Project name"));
  }

  @Test
  void parseSnapshotTimeHandlesIso8601() {
    var instant = SnapsPruneCommand.parseSnapshotTime("2026-04-07T03:58:31.123456789Z");

    assertNotNull(instant);
    assertTrue(instant.isBefore(Instant.now()));
  }

  @Test
  void parseSnapshotTimeHandlesOffsetDateTime() {
    var instant = SnapsPruneCommand.parseSnapshotTime("2026-04-07T03:58:31+00:00");

    assertNotNull(instant);
  }

  @Test
  void parseSnapshotTimeReturnsNullForNull() {
    assertNull(SnapsPruneCommand.parseSnapshotTime(null));
  }

  @Test
  void parseSnapshotTimeReturnsNullForBlank() {
    assertNull(SnapsPruneCommand.parseSnapshotTime(""));
  }

  @Test
  void parseSnapshotTimeReturnsNullForGarbage() {
    assertNull(SnapsPruneCommand.parseSnapshotTime("not-a-date"));
  }

  @Test
  void parseAgeLargeValues() {
    assertEquals(Duration.ofDays(365), SnapsPruneCommand.parseAge("365d"));
  }

  @Test
  void parseAgeErrorMessageIsHelpful() {
    var ex = assertThrows(IllegalArgumentException.class, () -> SnapsPruneCommand.parseAge("abc"));

    assertTrue(ex.getMessage().contains("Invalid age format"));
    assertTrue(ex.getMessage().contains("Examples"));
  }
}
