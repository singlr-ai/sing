/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing;

import ai.singlr.sing.commands.AgentCommand;
import ai.singlr.sing.commands.ApiCommand;
import ai.singlr.sing.commands.ClientInitCommand;
import ai.singlr.sing.commands.HostCommand;
import ai.singlr.sing.commands.ProjectCommand;
import ai.singlr.sing.commands.SpecCommand;
import ai.singlr.sing.commands.UpgradeCommand;
import ai.singlr.sing.engine.Banner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;

@Command(
    name = "sail",
    description = "Isolated dev environments for AI agents.",
    versionProvider = SingVersion.class,
    mixinStandardHelpOptions = true,
    subcommands = {
      ClientInitCommand.class,
      HostCommand.class,
      ProjectCommand.class,
      SpecCommand.class,
      AgentCommand.class,
      ApiCommand.class,
      UpgradeCommand.class,
    })
public final class Sing implements Runnable {

  @Override
  public void run() {
    Banner.printBranding(System.out, Ansi.AUTO);
    System.out.println();
    new picocli.CommandLine(this).usage(System.out);
  }
}
