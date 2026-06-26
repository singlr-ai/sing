/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.singlr.sail.config.SpecStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MissedStopsTest {

  private static SpecStore.SpecRow spec(String id, SpecStatus status) {
    return new SpecStore.SpecRow(
        id,
        "acme",
        "T",
        status,
        null,
        "claude-code",
        null,
        null,
        null,
        0,
        "me",
        null,
        null,
        "me",
        List.of(),
        List.of());
  }

  private static SessionStore.SessionRow session(String specId, String status, Integer exitCode) {
    return new SessionStore.SessionRow(
        "s-" + specId,
        "acme",
        specId,
        "claude-code",
        null,
        null,
        null,
        status,
        "t0",
        "t1",
        exitCode);
  }

  private static java.util.function.Function<String, Optional<SessionStore.SessionRow>> latest(
      Map<String, SessionStore.SessionRow> byId) {
    return id -> Optional.ofNullable(byId.get(id));
  }

  @Test
  void replaysAnInProgressSpecWhoseSessionStoppedCleanly() {
    var replays =
        MissedStops.find(
            List.of(spec("auth", SpecStatus.IN_PROGRESS)),
            latest(Map.of("auth", session("auth", "stopped", 0))));

    assertEquals(1, replays.size());
    assertEquals("auth", replays.getFirst().spec().id());
    assertEquals(0, replays.getFirst().exitCode());
  }

  @Test
  void carriesTheExitCodeOfACrashedSession() {
    var replays =
        MissedStops.find(
            List.of(spec("auth", SpecStatus.IN_PROGRESS)),
            latest(Map.of("auth", session("auth", "stopped", 137))));

    assertEquals(137, replays.getFirst().exitCode());
  }

  @Test
  void treatsCompletedAsTerminalToo() {
    var replays =
        MissedStops.find(
            List.of(spec("auth", SpecStatus.IN_PROGRESS)),
            latest(Map.of("auth", session("auth", "completed", null))));

    assertEquals(1, replays.size());
  }

  @Test
  void ignoresASpecWhoseSessionIsStillRunning() {
    var replays =
        MissedStops.find(
            List.of(spec("auth", SpecStatus.IN_PROGRESS)),
            latest(Map.of("auth", session("auth", "running", null))));

    assertTrue(replays.isEmpty());
  }

  @Test
  void ignoresASpecWithNoSession() {
    var replays = MissedStops.find(List.of(spec("auth", SpecStatus.IN_PROGRESS)), latest(Map.of()));

    assertTrue(replays.isEmpty());
  }

  @Test
  void ignoresSpecsThatAreNotInProgress() {
    var replays =
        MissedStops.find(
            List.of(spec("auth", SpecStatus.REVIEW), spec("done", SpecStatus.DONE)),
            latest(
                Map.of(
                    "auth", session("auth", "stopped", 0),
                    "done", session("done", "stopped", 0))));

    assertTrue(replays.isEmpty());
  }

  @Test
  void surfacesOnlyTheStrandedSpecAmongMany() {
    var replays =
        MissedStops.find(
            List.of(
                spec("stuck", SpecStatus.IN_PROGRESS),
                spec("active", SpecStatus.IN_PROGRESS),
                spec("fresh", SpecStatus.IN_PROGRESS)),
            latest(
                Map.of(
                    "stuck", session("stuck", "stopped", 0),
                    "active", session("active", "running", null))));

    assertEquals(List.of("stuck"), replays.stream().map(r -> r.spec().id()).toList());
  }
}
