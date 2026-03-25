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
      ProjectAddCommand.class,
      ProjectRemoveCommand.class,
      ProjectPullCommand.class,
      ProjectPushCommand.class,
      ProjectListCommand.class,
      ProjectDestroyCommand.class,
      ProjectConfigCommand.class,
      ProjectInstallAgentCommand.class,
      ProjectDemoCommand.class,
    })
public final class ProjectCommand implements Runnable {

  @Override
  public void run() {
    new picocli.CommandLine(this).usage(System.out);
  }
}
