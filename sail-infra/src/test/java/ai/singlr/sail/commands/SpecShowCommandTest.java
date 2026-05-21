/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.commands;

import static org.junit.jupiter.api.Assertions.*;

import ai.singlr.sail.config.SpecAuditEvent;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpecShowCommandTest {

  private static final Instant TS = Instant.parse("2026-05-21T12:34:56Z");
  private static final String HOST = "sail-host-01";

  @Test
  void printAuditSectionEmitsNothingForEmptyList() {
    var out = new ByteArrayOutputStream();

    SpecShowCommand.printAuditSection(
        List.of(), new PrintStream(out, true, StandardCharsets.UTF_8));

    assertEquals(0, out.size());
  }

  @Test
  void printAuditSectionEmitsHeaderAndLinesInOrder() {
    var first = new SpecAuditEvent(TS, "dispatched", "sail", null, HOST, null);
    var second = new SpecAuditEvent(TS.plusSeconds(60), "started", "claude-code", 42, HOST, null);
    var out = new ByteArrayOutputStream();

    SpecShowCommand.printAuditSection(
        List.of(first, second), new PrintStream(out, true, StandardCharsets.UTF_8));

    var rendered = out.toString(StandardCharsets.UTF_8);
    assertTrue(rendered.contains("Audit"));
    var dispatchedAt = rendered.indexOf("dispatched");
    var startedAt = rendered.indexOf("started");
    assertTrue(dispatchedAt > 0);
    assertTrue(startedAt > dispatchedAt, "events should render in chronological order");
  }

  @Test
  void formatAuditLineIncludesAgentTimestampAndHost() {
    var event = new SpecAuditEvent(TS, "dispatched", "sail", null, HOST, null);

    var line = SpecShowCommand.formatAuditLine(event);

    assertTrue(line.contains(TS.toString()));
    assertTrue(line.contains("dispatched"));
    assertTrue(line.contains("sail"));
    assertTrue(line.contains(HOST));
    assertFalse(line.contains("pid="));
    assertFalse(line.contains("("));
  }

  @Test
  void formatAuditLineIncludesPidWhenPresent() {
    var event = new SpecAuditEvent(TS, "started", "claude-code", 12345, HOST, null);

    var line = SpecShowCommand.formatAuditLine(event);

    assertTrue(line.contains("pid=12345"));
  }

  @Test
  void formatAuditLineIncludesNoteWhenPresent() {
    var event =
        new SpecAuditEvent(TS, "restarted", "sail", null, HOST, "restarted from in_progress");

    var line = SpecShowCommand.formatAuditLine(event);

    assertTrue(line.contains("(restarted from in_progress)"));
  }

  @Test
  void formatAuditLineOmitsBlankNote() {
    var event = new SpecAuditEvent(TS, "dispatched", "sail", null, HOST, null);

    var line = SpecShowCommand.formatAuditLine(event);

    assertFalse(line.contains("()"));
  }
}
