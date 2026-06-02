/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.ssh;

import ai.singlr.sail.store.AuthSessionStore;
import ai.singlr.sail.store.FdeStore;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Decides what a forced-command SSH session may run and as whom. When an engineer's key hits the
 * {@code sail} user's forced command, this resolves their FDE (passed as {@code --fde} on the
 * authorized_keys line), validates the requested command against an allow-list, mints a short-lived
 * session for that FDE, and returns the argument vector to exec with {@code SAIL_TOKEN} set — so
 * the downstream command authenticates to the loopback API as the FDE and {@code Authorizer}
 * enforces its role. Default-deny: only API-backed, role-governed commands are permitted;
 * database-direct admin commands ({@code fde}, {@code host}, {@code server}, {@code migrate}) are
 * not, because they would bypass the API and run with the {@code sail} user's full access
 * regardless of role.
 */
public final class SshGateway {

  static final Duration SESSION_TTL = Duration.ofMinutes(10);
  static final Set<String> ALLOWED_COMMANDS = Set.of("spec", "dispatch", "agent", "events");

  private SshGateway() {}

  public sealed interface Decision permits Authorized, Rejected {}

  /** The command is permitted; exec {@code args} with {@code SAIL_TOKEN} = {@code sessionToken}. */
  public record Authorized(List<String> args, String sessionToken) implements Decision {}

  /** The command is refused; {@code reason} is safe to show the caller. */
  public record Rejected(String reason) implements Decision {}

  public static Decision authorize(
      String originalCommand, String fdeHandle, FdeStore fdes, AuthSessionStore sessions) {
    if (originalCommand == null || originalCommand.isBlank()) {
      return new Rejected(
          "No command supplied. Interactive shells are not permitted; run a 'sail' command.");
    }
    List<String> tokens;
    try {
      tokens = CommandTokenizer.split(originalCommand);
    } catch (IllegalArgumentException e) {
      return new Rejected(e.getMessage());
    }
    if (!tokens.isEmpty() && tokens.getFirst().equals("sail")) {
      tokens = tokens.subList(1, tokens.size());
    }
    if (tokens.isEmpty()) {
      return new Rejected("No 'sail' subcommand supplied.");
    }
    var subcommand = tokens.getFirst();
    if (!ALLOWED_COMMANDS.contains(subcommand)) {
      return new Rejected("'" + subcommand + "' is not permitted over an SSH-key session.");
    }
    var fde = fdes.byHandle(fdeHandle);
    if (fde.isEmpty() || !"active".equals(fde.get().status())) {
      return new Rejected("Unknown or disabled FDE.");
    }
    var session = sessions.create(fde.get().id(), SESSION_TTL);
    return new Authorized(List.copyOf(tokens), session.token());
  }
}
