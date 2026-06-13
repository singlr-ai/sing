/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * One sync session's transport: an {@code ssh sail@main sail _sync} subprocess whose stdio is the
 * RPC pipe. The remote {@code _sync} server reads requests from this channel's {@link #writer} and
 * writes responses to its {@link #reader}; {@link ai.singlr.sail.sync.RemoteMainReplica} drives
 * both ends. Like the rest of the gateway lane, password and keyboard-interactive auth are disabled
 * so a missing key fails fast instead of dangling a prompt for the locked {@code sail} account.
 */
public final class SshSyncChannel implements AutoCloseable {

  private final Process process;
  private final BufferedReader reader;
  private final Writer writer;

  private SshSyncChannel(Process process) {
    this.process = process;
    this.reader =
        new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    this.writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
  }

  /** Opens a sync channel to {@code target} (e.g. {@code sail@maindevbox}). */
  public static SshSyncChannel open(String target) throws IOException {
    var builder = new ProcessBuilder(sshCommand(target));
    builder.redirectError(ProcessBuilder.Redirect.INHERIT);
    return new SshSyncChannel(builder.start());
  }

  /**
   * The {@code ssh} argument vector for a sync session. Like the rest of the gateway lane it
   * forbids password and keyboard-interactive auth, so a missing key fails fast rather than
   * dangling a prompt for the locked {@code sail} account.
   */
  static List<String> sshCommand(String target) {
    return List.of(
        "ssh",
        "-o",
        "PasswordAuthentication=no",
        "-o",
        "KbdInteractiveAuthentication=no",
        target,
        "sail",
        "_sync");
  }

  public BufferedReader reader() {
    return reader;
  }

  public Writer writer() {
    return writer;
  }

  /** Closes this node's end of the pipe and waits for the remote {@code _sync} to exit. */
  @Override
  public void close() throws IOException {
    writer.close();
    reader.close();
    try {
      process.waitFor();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      process.destroy();
    }
  }
}
