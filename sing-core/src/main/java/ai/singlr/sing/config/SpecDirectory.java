/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads and writes the {@code specs/} directory structure used for spec-driven agent work. The
 * directory contains an {@code index.yaml} listing spec IDs in order, and one subdirectory per spec
 * with {@code spec.yaml} (metadata) and optionally {@code spec.md} (detailed description).
 *
 * <p>This class is pure parsing/generation logic with no I/O — callers pass content strings in and
 * receive content strings back.
 */
public final class SpecDirectory {

  private SpecDirectory() {}

  /**
   * Parses the {@code index.yaml} content into an ordered list of specs. Each entry in the {@code
   * specs} list must have at least an {@code id} field.
   */
  @SuppressWarnings("unchecked")
  public static List<Spec> parseIndex(Map<String, Object> indexMap) {
    var rawSpecs = (List<Map<String, Object>>) indexMap.get("specs");
    if (rawSpecs == null || rawSpecs.isEmpty()) {
      return List.of();
    }
    return rawSpecs.stream().map(Spec::fromMap).toList();
  }

  /**
   * Generates the {@code index.yaml} content from an ordered list of specs. Only writes the fields
   * relevant to the index (id, title, status, assignee, depends_on, branch).
   */
  public static Map<String, Object> generateIndex(List<Spec> specs) {
    var map = new LinkedHashMap<String, Object>();
    map.put("specs", specs.stream().map(Spec::toMap).toList());
    return map;
  }

  /**
   * Returns the first pending spec whose dependencies are all done and whose assignee matches the
   * given identity (or is unassigned). Returns {@code null} if no spec is ready.
   *
   * @param specs ordered list of specs (from index)
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

  private static boolean dependenciesMet(Spec spec, Set<String> doneIds) {
    return spec.dependsOn().stream().allMatch(doneIds::contains);
  }
}
