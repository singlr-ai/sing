/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import picocli.CommandLine.Command;

@Command(
    name = "remove",
    description = "Remove services or repos from a running project.",
    mixinStandardHelpOptions = true,
    subcommands = {
      ProjectRemoveServiceCommand.class,
    })
public final class ProjectRemoveCommand implements Runnable {

  @Override
  public void run() {
    new picocli.CommandLine(this).usage(System.out);
  }
}
