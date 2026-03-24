/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import picocli.CommandLine.Command;

@Command(
    name = "config",
    description = "View or update host configuration.",
    mixinStandardHelpOptions = true,
    subcommands = {HostConfigSetCommand.class})
public final class HostConfigCommand implements Runnable {

  @Override
  public void run() {
    new picocli.CommandLine(this).usage(System.out);
  }
}
