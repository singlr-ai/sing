/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.store;

import ai.singlr.sail.config.SpecStatus;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Finds specs whose agent already finished but whose stop never advanced them — the {@code
 * agent_session_stopped} was lost because the control plane was down when the agent exited. The
 * authoritative signal is the session: a spec still {@code in_progress} whose latest session is
 * terminal ({@code stopped}/{@code completed}) means the run ended without the loop reacting.
 * Replaying a stop for it lets the normal review (or failure) path run at startup, rather than
 * waiting on the stranded-spec sweep.
 *
 * <p>Only {@code in_progress} specs are considered. A clean replay advances the spec out of {@code
 * in_progress} so it is not re-picked on the next start; a crash leaves it {@code in_progress} (its
 * failure already recorded on the session) where it is correctly still unresolved. A spec already
 * past the gate ({@code review}, {@code awaiting_merge}, {@code done}) is never replayed — a replay
 * would re-run a review it already passed.
 */
public final class MissedStops {

  private static final Set<String> TERMINAL = Set.of("stopped", "completed");

  /** A spec to replay a stop for, carrying the exit code its finished session recorded. */
  public record Replay(SpecStore.SpecRow spec, Integer exitCode) {}

  private MissedStops() {}

  /**
   * @param specs candidate specs (non-{@code in_progress} ones are ignored)
   * @param latestSession resolves a spec id to its most recent session, if any
   */
  public static List<Replay> find(
      List<SpecStore.SpecRow> specs,
      Function<String, Optional<SessionStore.SessionRow>> latestSession) {
    return specs.stream()
        .filter(spec -> spec.status() == SpecStatus.IN_PROGRESS)
        .flatMap(
            spec ->
                latestSession
                    .apply(spec.id())
                    .filter(session -> TERMINAL.contains(session.status()))
                    .map(session -> new Replay(spec, session.exitCode()))
                    .stream())
        .toList();
  }
}
