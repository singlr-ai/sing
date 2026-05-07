/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads and writes the {@code specs/} directory structure used for spec-driven agent work. Each
 * spec lives in its own directory with {@code spec.yaml} metadata and {@code spec.md} details.
 *
 * <p>This class is pure parsing/generation logic with no I/O — callers pass content strings in and
 * receive content strings back.
 */
public final class SpecDirectory {

  public static final Set<String> VALID_STATUSES =
      Set.of("pending", "in_progress", "review", "done");

  public record Summary(
      Map<String, Integer> counts, int readyCount, int blockedCount, String nextReadyId) {

    public Map<String, Object> toMap() {
      var map = new LinkedHashMap<String, Object>();
      map.put("counts", counts);
      map.put("ready_count", readyCount);
      map.put("blocked_count", blockedCount);
      if (nextReadyId != null) {
        map.put("next_ready_id", nextReadyId);
      }
      return map;
    }
  }

  private SpecDirectory() {}

  public static Spec parseMetadata(Map<String, Object> metadata) {
    return Spec.fromMap(metadata);
  }

  public static Map<String, Object> generateMetadata(Spec spec) {
    return spec.toMap();
  }

  /**
   * Returns the first pending spec whose dependencies are all done and whose assignee matches the
   * given identity (or is unassigned). Returns {@code null} if no spec is ready.
   *
   * @param specs ordered list of specs
   * @param assignee the engineer's identity to match (nullable for any assignee)
   */
  public static Spec nextReady(List<Spec> specs, String assignee) {
    var doneIds =
        specs.stream()
            .filter(s -> "done".equals(s.status()))
            .map(Spec::id)
            .collect(Collectors.toSet());

    for (var spec : specs) {
      if (!"pending".equals(spec.status())) {
        continue;
      }
      if (!dependenciesMet(spec, doneIds)) {
        continue;
      }
      if (assignee != null && spec.assignee() != null && !assignee.equals(spec.assignee())) {
        continue;
      }
      return spec;
    }
    return null;
  }

  /**
   * Returns the first pending spec whose dependencies are all done, regardless of assignee. Returns
   * {@code null} if no spec is ready.
   */
  public static Spec nextReady(List<Spec> specs) {
    return nextReady(specs, null);
  }

  /** Returns the spec with the given id, or null if not found. */
  public static Spec findById(List<Spec> specs, String specId) {
    return specs.stream().filter(spec -> spec.id().equals(specId)).findFirst().orElse(null);
  }

  /** Returns a copy of the specs list with the given spec's status updated. */
  public static List<Spec> updateStatus(List<Spec> specs, String specId, String newStatus) {
    requireValidStatus(newStatus);
    if (findById(specs, specId) == null) {
      throw new IllegalArgumentException("Spec '" + specId + "' not found");
    }
    return specs.stream()
        .map(
            spec ->
                spec.id().equals(specId)
                    ? new Spec(
                        spec.id(),
                        spec.title(),
                        newStatus,
                        spec.assignee(),
                        spec.dependsOn(),
                        spec.repos(),
                        spec.branch())
                    : spec)
        .toList();
  }

  /** Returns true if the spec is pending and all dependencies are done. */
  public static boolean isReady(List<Spec> specs, Spec spec) {
    if (!"pending".equals(spec.status())) {
      return false;
    }
    var doneIds = doneIds(specs);
    return dependenciesMet(spec, doneIds);
  }

  /** Returns true if the spec is pending and is blocked by unmet dependencies. */
  public static boolean isBlocked(List<Spec> specs, Spec spec) {
    if (!"pending".equals(spec.status()) || spec.dependsOn().isEmpty()) {
      return false;
    }
    var doneIds = doneIds(specs);
    return !dependenciesMet(spec, doneIds);
  }

  /** Returns the list of unmet dependency ids for the given spec. */
  public static List<String> unmetDependencies(List<Spec> specs, Spec spec) {
    var doneIds = doneIds(specs);
    return spec.dependsOn().stream().filter(dep -> !doneIds.contains(dep)).toList();
  }

  /** Returns readiness and blocked-state summary information for a board. */
  public static Summary summarize(List<Spec> specs) {
    var readyCount = 0;
    var blockedCount = 0;
    for (var spec : specs) {
      if (isReady(specs, spec)) {
        readyCount++;
      } else if (isBlocked(specs, spec)) {
        blockedCount++;
      }
    }
    var nextReady = nextReady(specs);
    return new Summary(
        statusCounts(specs), readyCount, blockedCount, nextReady != null ? nextReady.id() : null);
  }

  /** Returns a map of status counts: {pending: N, in_progress: N, review: N, done: N}. */
  public static Map<String, Integer> statusCounts(List<Spec> specs) {
    var counts = new LinkedHashMap<String, Integer>();
    counts.put("pending", 0);
    counts.put("in_progress", 0);
    counts.put("review", 0);
    counts.put("done", 0);
    for (var spec : specs) {
      counts.merge(spec.status(), 1, Integer::sum);
    }
    return counts;
  }

  /** Validates a lifecycle status that can be set by CLI commands. */
  public static void requireValidStatus(String status) {
    if (status == null || !VALID_STATUSES.contains(status)) {
      throw new IllegalArgumentException(
          "Invalid spec status: '"
              + status
              + "'. Must be one of: "
              + String.join(", ", VALID_STATUSES));
    }
  }

  private static Set<String> doneIds(List<Spec> specs) {
    return specs.stream()
        .filter(s -> "done".equals(s.status()))
        .map(Spec::id)
        .collect(Collectors.toSet());
  }

  private static boolean dependenciesMet(Spec spec, Set<String> doneIds) {
    return spec.dependsOn().stream().allMatch(doneIds::contains);
  }
}
