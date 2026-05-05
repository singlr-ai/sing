/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deep-merges two YAML maps (parsed from {@code global.yaml} and a project {@code sail.yaml}).
 * Override wins for scalars, maps are merged recursively, and lists are unioned (deduplicated).
 * Neither input map is mutated.
 */
public final class YamlMerger {

  private YamlMerger() {}

  /**
   * Deep-merges {@code base} with {@code override}. For each key:
   *
   * <ul>
   *   <li>Both maps → recursive merge
   *   <li>Both lists → union (base items first, override items appended, duplicates skipped)
   *   <li>Override is non-null → override wins
   *   <li>Override is null or key absent → base preserved
   * </ul>
   *
   * @param base the global defaults map
   * @param override the project-specific overrides map
   * @return a new merged map
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> deepMerge(
      Map<String, Object> base, Map<String, Object> override) {
    var result = new LinkedHashMap<String, Object>(base);

    for (var entry : override.entrySet()) {
      var key = entry.getKey();
      var overrideVal = entry.getValue();
      var baseVal = result.get(key);

      if (overrideVal == null) {
        continue;
      }

      if (baseVal instanceof Map<?, ?> baseMap && overrideVal instanceof Map<?, ?> overrideMap) {
        result.put(
            key, deepMerge((Map<String, Object>) baseMap, (Map<String, Object>) overrideMap));
      } else if (baseVal instanceof List<?> baseList
          && overrideVal instanceof List<?> overrideList) {
        result.put(key, unionLists(baseList, overrideList));
      } else {
        result.put(key, overrideVal);
      }
    }

    return result;
  }

  /** Returns a new list with base items first, then override items not already present. */
  private static List<Object> unionLists(List<?> base, List<?> override) {
    var result = new ArrayList<Object>(base);
    for (var item : override) {
      if (!result.contains(item)) {
        result.add(item);
      }
    }
    return result;
  }
}
