/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import picocli.CommandLine.Command;

@Command(
    name = "host",
    description = "Manage the bare-metal host server.",
    mixinStandardHelpOptions = true,
    subcommands = {
      HostInitCommand.class,
      HostStatusCommand.class,
      HostUpdateCommand.class,
      HostConfigCommand.class
    })
public final class HostCommand implements Runnable {

  @Override
  public void run() {
    new picocli.CommandLine(this).usage(System.out);
  }
}
