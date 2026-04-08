/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing;

import ai.singlr.sing.commands.AgentCommand;
import ai.singlr.sing.commands.ClientInitCommand;
import ai.singlr.sing.commands.ConnectCommand;
import ai.singlr.sing.commands.DispatchCommand;
import ai.singlr.sing.commands.DownCommand;
import ai.singlr.sing.commands.ExecCommand;
import ai.singlr.sing.commands.HostCommand;
import ai.singlr.sing.commands.LogsCommand;
import ai.singlr.sing.commands.ProjectCommand;
import ai.singlr.sing.commands.PsCommand;
import ai.singlr.sing.commands.RestoreCommand;
import ai.singlr.sing.commands.RunCommand;
import ai.singlr.sing.commands.ShellCommand;
import ai.singlr.sing.commands.SnapCommand;
import ai.singlr.sing.commands.SnapsCommand;
import ai.singlr.sing.commands.SnapsPruneCommand;
import ai.singlr.sing.commands.SpecCommand;
import ai.singlr.sing.commands.SwitchCommand;
import ai.singlr.sing.commands.UpCommand;
import ai.singlr.sing.commands.UpgradeCommand;
import ai.singlr.sing.engine.Banner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;

@Command(
    name = "sing",
    description = "Isolated dev environments for AI agents.",
    versionProvider = SingVersion.class,
    mixinStandardHelpOptions = true,
    subcommands = {
      ClientInitCommand.class,
      HostCommand.class,
      ProjectCommand.class,
      UpCommand.class,
      DownCommand.class,
      SwitchCommand.class,
      PsCommand.class,
      LogsCommand.class,
      SnapCommand.class,
      RestoreCommand.class,
      SnapsCommand.class,
      SnapsPruneCommand.class,
      RunCommand.class,
      AgentCommand.class,
      DispatchCommand.class,
      SpecCommand.class,
      ExecCommand.class,
      ShellCommand.class,
      ConnectCommand.class,
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
