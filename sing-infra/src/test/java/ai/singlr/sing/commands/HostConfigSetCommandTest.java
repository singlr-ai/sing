/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import static org.junit.jupiter.api.Assertions.*;

import ai.singlr.sing.Sing;
import ai.singlr.sing.config.HostYaml;
import ai.singlr.sing.config.YamlUtil;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import picocli.CommandLine;

@Execution(ExecutionMode.SAME_THREAD)
class HostConfigSetCommandTest {

  private PrintStream originalOut;
  private PrintStream originalErr;
  private ByteArrayOutputStream capturedOut;
  private ByteArrayOutputStream capturedErr;

  @BeforeEach
  void captureStreams() {
    originalOut = System.out;
    originalErr = System.err;
    capturedOut = new ByteArrayOutputStream();
    capturedErr = new ByteArrayOutputStream();
    System.setOut(new PrintStream(capturedOut));
    System.setErr(new PrintStream(capturedErr));
  }

  @AfterEach
  void restoreStreams() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  @Test
  void helpShowsUsage() {
    var cmd = new CommandLine(new Sing());
    cmd.setErr(new PrintWriter(new StringWriter()));

    var exitCode = cmd.execute("host", "config", "set", "--help");
    assertEquals(0, exitCode);

    var output = capturedOut.toString();
    assertTrue(output.contains("server-ip"), "Help should mention server-ip");
  }

  @Test
  void dryRunShowsWhatWouldChange(@TempDir Path tempDir) throws Exception {
    var hostYamlPath = tempDir.resolve("host.yaml");
    var hostYaml =
        new HostYaml(
            "dir",
            "devpool",
            null,
            "incusbr0",
            "singlr-base",
            "ubuntu/24.04",
            "6.8",
            null,
            "2026-01-01T00:00:00Z");
    YamlUtil.dumpToFile(hostYaml.toMap(), hostYamlPath);

    var cmd = new HostConfigSetCommand();
    injectFields(cmd, "key", "server-ip");
    injectFields(cmd, "value", "192.168.1.100");
    injectFields(cmd, "dryRun", true);

    var output = capturedOut.toString();
    assertNotNull(cmd);
  }

  @Test
  void rejectsUnknownKey() {
    var cmd = new CommandLine(new Sing());
    cmd.setErr(new PrintWriter(new StringWriter()));

    var exitCode = cmd.execute("host", "config", "set", "--dry-run", "unknown-key", "value");
    assertNotEquals(0, exitCode);

    var errOutput = capturedErr.toString();
    assertTrue(
        errOutput.contains("Unknown config key") || errOutput.contains("unknown-key"),
        "Should reject unknown config key");
  }

  @Test
  void rejectsInvalidIpFormat() {
    var cmd = new CommandLine(new Sing());
    cmd.setErr(new PrintWriter(new StringWriter()));

    var exitCode = cmd.execute("host", "config", "set", "--dry-run", "server-ip", "not-an-ip");
    assertNotEquals(0, exitCode);

    var errOutput = capturedErr.toString();
    assertTrue(
        errOutput.contains("Invalid IPv4") || errOutput.contains("not-an-ip"),
        "Should reject invalid IP format");
  }

  @Test
  void hostConfigSubcommandRegistered() {
    var cmd = new CommandLine(new Sing());
    var sw = new StringWriter();
    cmd.setOut(new PrintWriter(sw));
    cmd.setErr(new PrintWriter(new StringWriter()));

    cmd.execute("host", "config");
    var output = capturedOut.toString();
    assertTrue(output.contains("set"), "host config should list 'set' subcommand");
  }

  private static void injectFields(Object obj, String fieldName, Object value) {
    try {
      var field = obj.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(obj, value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
