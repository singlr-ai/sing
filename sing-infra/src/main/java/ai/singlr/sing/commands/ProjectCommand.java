/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import picocli.CommandLine.Command;

@Command(
    name = "project",
    description = "Manage project environments.",
    mixinStandardHelpOptions = true,
    subcommands = {
      ProjectInitCommand.class,
      ProjectCreateCommand.class,
      ProjectApplyCommand.class,
      UpCommand.class,
      DownCommand.class,
      SwitchCommand.class,
      ProjectListCommand.class,
      PsCommand.class,
      ProjectConfigCommand.class,
      LogsCommand.class,
      ExecCommand.class,
      ShellCommand.class,
      ConnectCommand.class,
      ProjectSnapshotCommand.class,
      ProjectAddCommand.class,
      ProjectRemoveCommand.class,
      ProjectResourcesCommand.class,
      ProjectPullCommand.class,
      ProjectPushCommand.class,
      ProjectDestroyCommand.class,
      ProjectInstallAgentCommand.class,
      ProjectDemoCommand.class,
    })
public final class ProjectCommand implements Runnable {

  @Override
  public void run() {
    new picocli.CommandLine(this).usage(System.out);
  }
}
