/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Contract for executing shell commands. Production code depends on this interface, enabling test
 * doubles without subclassing.
 */
public interface ShellExec {

  /** Result of a completed shell command. */
  record Result(int exitCode, String stdout, String stderr) {
    public boolean ok() {
      return exitCode == 0;
    }
  }

  /**
   * Executes a command with the default timeout and no specific working directory.
   *
   * @param command the command tokens (e.g. {@code List.of("incus", "list", "--format", "json")})
   * @return the command result
   */
  Result exec(List<String> command) throws IOException, InterruptedException, TimeoutException;

  /**
   * Executes a command with a specific working directory and timeout.
   *
   * @param command the command tokens
   * @param workDir optional working directory (null = inherit)
   * @param timeout how long to wait before killing the process
   * @return the command result
   */
  Result exec(List<String> command, Path workDir, Duration timeout)
      throws IOException, InterruptedException, TimeoutException;

  /** Returns true if this executor is in dry-run mode (prints commands instead of executing). */
  boolean isDryRun();
}
