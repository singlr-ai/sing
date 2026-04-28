/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import picocli.CommandLine.Command;

@Command(
    name = "snapshot",
    description = "Manage project container snapshots.",
    mixinStandardHelpOptions = true,
    subcommands = {
      SnapCommand.class,
      SnapsCommand.class,
      RestoreCommand.class,
      SnapsPruneCommand.class,
    })
public final class ProjectSnapshotCommand implements Runnable {

  @Override
  public void run() {
    new picocli.CommandLine(this).usage(System.out);
  }
}
