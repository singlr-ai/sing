/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import picocli.CommandLine.Command;

@Command(
    name = "spec",
    description = "Manage project specs.",
    mixinStandardHelpOptions = true,
    subcommands = {
      SpecListCommand.class,
    })
public final class SpecCommand implements Runnable {

  @Override
  public void run() {
    new picocli.CommandLine(this).usage(System.out);
  }
}
