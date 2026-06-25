/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.store;

import ai.singlr.sail.config.SpecStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Finds specs stranded mid-flight — left in {@code in_progress} or {@code review} long past when a
 * dispatch should have advanced them. A spec's {@code updated_at} is stamped when it enters those
 * states and is not touched again while the agent works, so it doubles as the time the spec became
 * active. Beyond a generous threshold (longer than the watchdog ceiling) a still-active spec means
 * something silently failed — a Stop hook that never fired, a watcher that died, a review that
 * crashed — and the spec needs surfacing rather than waiting forever.
 */
public final class StrandedSpecs {

  private static final Set<SpecStatus> ACTIVE = Set.of(SpecStatus.IN_PROGRESS, SpecStatus.REVIEW);

  private StrandedSpecs() {}

  /**
   * Returns the specs that are {@code in_progress} or {@code review} and whose {@code updated_at}
   * is older than {@code now - threshold}. Specs with an unparseable or missing timestamp are not
   * flagged — a stranded spec is surfaced, never invented.
   */
  public static List<SpecStore.SpecRow> find(
      List<SpecStore.SpecRow> specs, Instant now, Duration threshold) {
    var cutoff = now.minus(threshold);
    return specs.stream()
        .filter(s -> ACTIVE.contains(s.status()))
        .filter(s -> isOlderThan(s.updatedAt(), cutoff))
        .toList();
  }

  private static boolean isOlderThan(String updatedAt, Instant cutoff) {
    if (updatedAt == null || updatedAt.isBlank()) {
      return false;
    }
    try {
      return Instant.parse(updatedAt).isBefore(cutoff);
    } catch (Exception e) {
      return false;
    }
  }
}
