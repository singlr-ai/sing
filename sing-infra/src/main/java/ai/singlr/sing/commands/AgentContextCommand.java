/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import picocli.CommandLine.Command;

@Command(
    name = "context",
    description = "Manage agent context files.",
    mixinStandardHelpOptions = true,
    subcommands = {
      AgentContextRegenCommand.class,
    })
public final class AgentContextCommand implements Runnable {

  @Override
  public void run() {
    new picocli.CommandLine(this).usage(System.out);
  }
}
