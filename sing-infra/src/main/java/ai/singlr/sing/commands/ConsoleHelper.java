/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Shared utilities for interactive CLI commands — stdin reading, confirmation prompts, and
 * privilege checks. Not thread-safe; intended for single-threaded CLI use.
 */
final class ConsoleHelper {

  private static BufferedReader stdinReader;

  private ConsoleHelper() {}

  /**
   * Prompts the user for confirmation. Returns {@code true} on empty input, 'y', or 'Y'. Returns
   * {@code false} on EOF (null) to prevent destructive operations from proceeding on piped empty
   * stdin.
   */
  static boolean confirm(String prompt) {
    System.out.print("  " + prompt + " [Y/n] ");
    System.out.flush();
    var line = readLine();
    if (line == null) {
      return false;
    }
    return line.isBlank() || line.strip().equalsIgnoreCase("y");
  }

  /** Reads a single line from stdin, or returns {@code null} on error/EOF. */
  static String readLine() {
    try {
      if (stdinReader == null) {
        stdinReader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
      }
      return stdinReader.readLine();
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Reads a password from the console with echo disabled. Falls back to {@link #readLine()} if
   * {@code System.console()} is null (e.g., piped input).
   */
  static String readPassword(String prompt) {
    System.out.print(prompt);
    System.out.flush();
    var console = System.console();
    if (console != null) {
      var chars = console.readPassword();
      return chars != null ? new String(chars) : null;
    }
    return readLine();
  }

  /**
   * Prompts the user for confirmation with default NO. Returns {@code true} only on explicit 'y' or
   * 'Y'. Returns {@code false} on empty input or EOF. Use for optional/additive prompts where the
   * safe default is to skip.
   */
  static boolean confirmNo(String prompt) {
    System.out.print("  " + prompt + " [y/N] ");
    System.out.flush();
    var line = readLine();
    if (line == null) {
      return false;
    }
    return line.strip().equalsIgnoreCase("y");
  }

  /** Returns {@code true} if the current process is running as root. */
  static boolean isRoot() {
    return "root".equals(ProcessHandle.current().info().user().orElse(""));
  }
}
