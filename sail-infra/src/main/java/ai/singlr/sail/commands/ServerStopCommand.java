/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(
    name = "stop",
    description = "Stop the running Sail server.",
    mixinStandardHelpOptions = true)
public final class ServerStopCommand implements Runnable {

  @Spec private CommandSpec spec;

  @Override
  public void run() {
    CliCommand.run(
        spec,
        () -> {
          System.out.println(
              Ansi.AUTO.string(
                  "  @|yellow ⚠|@ Server stop via CLI is not yet implemented. Use Ctrl+C or"
                      + " 'systemctl --user stop sail-server'."));
        });
  }
}
