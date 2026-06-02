/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.commands;

import ai.singlr.sail.engine.AuthorizedKeysRenderer;
import ai.singlr.sail.engine.SailPaths;
import ai.singlr.sail.engine.ShellExecutor;
import ai.singlr.sail.engine.SshIdentityProvisioner;
import ai.singlr.sail.store.FdeSshKeyStore;
import ai.singlr.sail.store.Sqlite;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Renders and installs the {@code sail} user's {@code authorized_keys} from the SSH-key registry,
 * so the keys an admin has registered (and only those) can log in as their FDE through the gateway.
 * Idempotent — it fully regenerates the managed file each run, so adding or removing a key and
 * re-syncing is the revocation path.
 */
@Command(
    name = "keys",
    description = "Manage the sail user's SSH authorized_keys.",
    mixinStandardHelpOptions = true,
    subcommands = {HostKeysCommand.Sync.class})
public final class HostKeysCommand implements Runnable {

  @Override
  public void run() {
    new picocli.CommandLine(this).usage(System.out);
  }

  @Command(
      name = "sync",
      description = "Regenerate the sail user's authorized_keys from registered FDE keys.",
      mixinStandardHelpOptions = true)
  static final class Sync implements Runnable {

    @Option(names = "--dry-run", description = "Print the authorized_keys instead of writing it.")
    private boolean dryRun;

    @Spec private CommandSpec spec;

    @Override
    public void run() {
      CliCommand.run(
          spec,
          () -> {
            try (var db = Sqlite.open(SailPaths.controlPlaneDb())) {
              var keys = new FdeSshKeyStore(db).list();
              var content = AuthorizedKeysRenderer.render(keys, SailPaths.binaryPath().toString());
              if (dryRun) {
                System.out.print(content);
                return;
              }
              install(content, keys.size());
            }
          });
    }

    private static void install(String content, int count) throws Exception {
      if (!"root".equals(ProcessHandle.current().info().user().orElse(""))) {
        throw new IllegalStateException(
            "Writing the sail user's authorized_keys requires root. Run it on the host as root,"
                + " or preview with --dry-run.");
      }
      var dest = Path.of(SshIdentityProvisioner.SAIL_HOME, ".ssh", "authorized_keys");
      if (!Files.isDirectory(dest.getParent())) {
        throw new IllegalStateException(
            "The sail user is not provisioned. Run 'sail host ssh-identity --apply' first.");
      }
      var temp = Files.createTempFile("sail-authorized-keys", "");
      try {
        Files.writeString(temp, content);
        var result =
            new ShellExecutor(false)
                .exec(
                    List.of(
                        "install",
                        "-m",
                        "600",
                        "-o",
                        "sail",
                        "-g",
                        "sail",
                        temp.toString(),
                        dest.toString()));
        if (!result.ok()) {
          throw new IllegalStateException("Failed to write " + dest + ":\n" + result.stderr());
        }
      } finally {
        Files.deleteIfExists(temp);
      }
      System.out.println(Ansi.AUTO.string("  @|green ✓|@ Synced " + count + " key(s) to " + dest));
    }
  }
}
