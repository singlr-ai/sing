/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Command;

class CliCommandTest {

  private PrintStream originalErr;
  private ByteArrayOutputStream capturedErr;

  @BeforeEach
  void captureErr() {
    originalErr = System.err;
    capturedErr = new ByteArrayOutputStream();
    System.setErr(new PrintStream(capturedErr));
  }

  @AfterEach
  void restoreErr() {
    System.setErr(originalErr);
  }

  @Test
  void wrapsFailuresAsExecutionExceptions() {
    var command = new CommandLine(new TestCommand());
    var spec = command.getCommandSpec();

    var error =
        assertThrows(
            CommandLine.ExecutionException.class,
            () ->
                CliCommand.run(
                    spec,
                    () -> {
                      throw new IllegalStateException("boom");
                    }));

    assertEquals("boom", error.getMessage());
    assertTrue(capturedErr.toString(StandardCharsets.UTF_8).contains("boom"));
  }

  @Test
  void usesExceptionTypeWhenMessageIsMissing() {
    var command = new CommandLine(new TestCommand());
    var spec = command.getCommandSpec();

    var error =
        assertThrows(
            CommandLine.ExecutionException.class,
            () ->
                CliCommand.run(
                    spec,
                    () -> {
                      throw new IllegalStateException();
                    }));

    assertEquals("IllegalStateException", error.getMessage());
  }

  @Test
  void printsFailureHintsWhenProvided() {
    var command = new CommandLine(new TestCommand());
    var spec = command.getCommandSpec();

    assertThrows(
        CommandLine.ExecutionException.class,
        () ->
            CliCommand.run(
                spec,
                "try --dry-run",
                () -> {
                  throw new IllegalStateException("boom");
                }));

    assertTrue(capturedErr.toString(StandardCharsets.UTF_8).contains("try --dry-run"));
  }

  @Command(name = "test")
  private static final class TestCommand implements Runnable {
    @Override
    public void run() {}
  }
}
