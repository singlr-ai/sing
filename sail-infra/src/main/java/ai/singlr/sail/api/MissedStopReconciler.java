/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.api;

import ai.singlr.sail.engine.HostInfo;
import ai.singlr.sail.store.MissedStops;
import ai.singlr.sail.store.SessionStore;
import ai.singlr.sail.store.SpecStore;
import java.util.Map;

/**
 * One-shot startup recovery: replays the stop events that were lost while the control plane was
 * down. For every {@code in_progress} spec whose latest session already finished (see {@link
 * MissedStops}), it publishes a synthetic {@code agent_session_stopped} — carrying the session's
 * exit code — onto the bus, so the same subscribers that handle a live stop ({@link
 * ReviewPipelineController}, {@link SessionTracker}) drive the spec to its real outcome instead of
 * leaving it parked until the stranded-spec sweep.
 *
 * <p>Run after the bus subscribers are wired, once, at server start.
 */
public final class MissedStopReconciler {

  private static final SpecStore.SpecFilter IN_PROGRESS =
      new SpecStore.SpecFilter(null, "in_progress", null, null, null);

  private final SpecStore specStore;
  private final SessionStore sessionStore;
  private final EventBus bus;

  public MissedStopReconciler(SpecStore specStore, SessionStore sessionStore, EventBus bus) {
    this.specStore = specStore;
    this.sessionStore = sessionStore;
    this.bus = bus;
  }

  /** Replays a stop for each spec with a missed one. Returns how many were replayed. */
  public int reconcile() {
    var replays =
        MissedStops.find(
            specStore.list(IN_PROGRESS),
            specId -> sessionStore.listForSpec(specId).stream().findFirst());
    for (var replay : replays) {
      var spec = replay.spec();
      System.err.println(
          "  [startup] replaying missed stop for "
              + spec.project()
              + "/"
              + spec.id()
              + (replay.exitCode() != null ? " (exit " + replay.exitCode() + ")" : ""));
      bus.publish(stopEvent(replay));
    }
    return replays.size();
  }

  static Event stopEvent(MissedStops.Replay replay) {
    var spec = replay.spec();
    var agent = spec.agent() != null ? spec.agent() : Event.SAIL_AGENT;
    var data =
        replay.exitCode() != null
            ? Map.<String, Object>of("exit_code", replay.exitCode(), "source", "startup-reconcile")
            : Map.<String, Object>of("source", "startup-reconcile");
    return Event.of(
        spec.project(),
        spec.id(),
        Event.WellKnownTypes.AGENT_SESSION_STOPPED,
        agent,
        HostInfo.hostname(),
        data);
  }
}
