/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import picocli.CommandLine.Command;

@Command(
    name = "add",
    description = "Add services, repos, or files to a running project.",
    mixinStandardHelpOptions = true,
    subcommands = {
      ProjectAddServiceCommand.class,
      ProjectAddRepoCommand.class,
      ProjectAddFileCommand.class,
    })
public final class ProjectAddCommand implements Runnable {

  @Override
  public void run() {
    new picocli.CommandLine(this).usage(System.out);
  }
}
