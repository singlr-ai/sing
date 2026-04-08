/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing;

import ai.singlr.sing.engine.AutoUpgrader;
import ai.singlr.sing.engine.RemoteCommandRunner;
import ai.singlr.sing.engine.RuntimeMode;
import picocli.CommandLine;

public final class Main {

  private Main() {}

  public static void main(String[] args) {
    if (RuntimeMode.detect() instanceof RuntimeMode.Client client) {
      System.exit(new RemoteCommandRunner(client.config()).execute(args));
    }
    AutoUpgrader.upgradeIfAvailable(args);
    var cmd = new CommandLine(new Sing());
    cmd.setExecutionExceptionHandler((ex, commandLine, parseResult) -> 1);
    var exitCode = cmd.execute(args);
    System.exit(exitCode);
  }
}
