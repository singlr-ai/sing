/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import ai.singlr.sing.config.ClientConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import picocli.CommandLine.Help.Ansi;

/**
 * Executes SAIL commands on a remote host via SSH. Used in client mode when the binary detects it
 * is running on a Mac (or any machine without {@code /etc/sing/host.yaml}).
 *
 * <p>Commands are forwarded as-is to the host. SSH handles transport, auth, and TTY allocation.
 * Output passes through — the remote host renders ANSI colors and the local terminal displays them.
 */
public final class RemoteCommandRunner {

  private static final Set<String> LOCAL_COMMANDS = Set.of("--version", "-V", "upgrade", "init");
  private static final Set<String> INTERACTIVE_COMMANDS = Set.of("shell", "exec");
  private static final Set<String> HOST_ONLY_COMMANDS = Set.of("host");
  private static final int SSH_CONNECTION_FAILURE = 255;

  private final ClientConfig config;

  public RemoteCommandRunner(ClientConfig config) {
    this.config = config;
  }

  /**
   * Executes a SAIL command. Returns the process exit code.
   *
   * <p>Routes commands into three categories:
   *
   * <ul>
   *   <li>Local: {@code --version}, {@code upgrade} — run on the client, not forwarded
   *   <li>Host-only: {@code host init}, {@code host config} — error with guidance
   *   <li>Everything else: forwarded to the remote host via SSH
   * </ul>
   */
  public int execute(String[] args) {
    if (args.length == 0) {
      return executeRemote(args, false);
    }

    var command = args[0];

    if (LOCAL_COMMANDS.contains(command)) {
      return executeLocal(args);
    }

    if (HOST_ONLY_COMMANDS.contains(command)) {
      System.err.println(
          Banner.errorLine(
              "The '"
                  + command
                  + "' command manages the host server directly."
                  + "\n  SSH to "
                  + config.host()
                  + " and run it there.",
              Ansi.AUTO));
      return 1;
    }

    var interactive = INTERACTIVE_COMMANDS.contains(command);
    return executeRemote(args, interactive);
  }

  private int executeLocal(String[] args) {
    try {
      var cmd = new ArrayList<String>();
      cmd.add(SingPaths.binaryPath().toString());
      cmd.addAll(List.of(args));
      var pb = new ProcessBuilder(cmd);
      pb.inheritIO();
      pb.environment().put("SING_CLIENT_LOCAL", "1");
      return pb.start().waitFor();
    } catch (Exception e) {
      System.err.println(
          Banner.errorLine("Failed to run local command: " + e.getMessage(), Ansi.AUTO));
      return 1;
    }
  }

  int executeRemote(String[] args, boolean interactive) {
    try {
      var cmd = buildSshCommand(args, interactive);
      var pb = new ProcessBuilder(cmd);
      pb.inheritIO();
      var exitCode = pb.start().waitFor();
      if (exitCode == SSH_CONNECTION_FAILURE) {
        System.err.println(
            Banner.errorLine(
                "Cannot connect to "
                    + config.host()
                    + "."
                    + "\n  Check that the host is reachable and your SSH key is configured."
                    + "\n  Config: "
                    + SingPaths.clientConfigPath(),
                Ansi.AUTO));
      }
      return exitCode;
    } catch (IOException e) {
      System.err.println(
          Banner.errorLine("SSH not found. Install OpenSSH: brew install openssh", Ansi.AUTO));
      return 1;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return 130;
    }
  }

  List<String> buildSshCommand(String[] args, boolean interactive) {
    var cmd = new ArrayList<String>();
    cmd.add("ssh");
    if (interactive) {
      cmd.add("-t");
    }
    cmd.add(config.host());
    cmd.add("sail");
    cmd.addAll(List.of(args));
    return List.copyOf(cmd);
  }

  /** Returns the classification of a command for testing. */
  static boolean isLocalCommand(String command) {
    return LOCAL_COMMANDS.contains(command);
  }

  /** Returns the classification of a command for testing. */
  static boolean isHostOnlyCommand(String command) {
    return HOST_ONLY_COMMANDS.contains(command);
  }

  /** Returns the classification of a command for testing. */
  static boolean isInteractiveCommand(String command) {
    return INTERACTIVE_COMMANDS.contains(command);
  }
}
