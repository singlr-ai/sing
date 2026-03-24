/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing;

import ai.singlr.sing.engine.AutoUpgrader;
import picocli.CommandLine;

public final class Main {

  private Main() {}

  public static void main(String[] args) {
    AutoUpgrader.upgradeIfAvailable(args);
    var cmd = new CommandLine(new Sing());
    cmd.setExecutionExceptionHandler((ex, commandLine, parseResult) -> 1);
    var exitCode = cmd.execute(args);
    System.exit(exitCode);
  }
}
