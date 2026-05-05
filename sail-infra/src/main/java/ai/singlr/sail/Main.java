/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail;

import ai.singlr.sail.engine.AutoUpgrader;
import ai.singlr.sail.engine.RemoteCommandRunner;
import ai.singlr.sail.engine.RuntimeMode;
import picocli.CommandLine;

public final class Main {

  private Main() {}

  public static void main(String[] args) {
    if (RuntimeMode.detect() instanceof RuntimeMode.Client client) {
      System.exit(new RemoteCommandRunner(client.config()).execute(args));
    }
    AutoUpgrader.upgradeIfAvailable(args);
    var cmd = new CommandLine(new Sail());
    cmd.setExecutionExceptionHandler((ex, commandLine, parseResult) -> 1);
    var exitCode = cmd.execute(args);
    System.exit(exitCode);
  }
}
