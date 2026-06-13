/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.sync;

import ai.singlr.sail.config.YamlUtil;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure helpers for the conflict-resolution UX, working over the comparable snapshots parked by the
 * sync engine. {@link #diff} drives {@code sail conflicts show} — for each field that moved, what
 * it was at the common base versus what each side made it, flagged when both sides clashed. {@link
 * #mergeTemplate} pre-fills the {@code --merge} editor with a field-level three-way merge: disjoint
 * edits are already applied, clashing fields default to yours with theirs shown in a comment, and
 * the result round-trips back through {@link YamlUtil#parseMap}. No I/O, no editor — just text.
 */
public final class ConflictMerge {

  private ConflictMerge() {}

  /** One field that changed on at least one side since the common base. */
  public record FieldChange(String field, Object base, Object mine, Object theirs, boolean clash) {}

  public static List<FieldChange> diff(
      Map<String, Object> base,
      Map<String, Object> mine,
      Map<String, Object> theirs,
      List<String> clashingFields) {
    var safeBase = base == null ? Map.<String, Object>of() : base;
    var changes = new java.util.ArrayList<FieldChange>();
    for (var field : allKeys(safeBase, mine, theirs)) {
      var atBase = safeBase.get(field);
      var atMine = value(mine, field);
      var atTheirs = value(theirs, field);
      if (!Objects.equals(atBase, atMine) || !Objects.equals(atBase, atTheirs)) {
        changes.add(
            new FieldChange(field, atBase, atMine, atTheirs, clashingFields.contains(field)));
      }
    }
    return List.copyOf(changes);
  }

  /**
   * The editable three-way merge for {@code --merge}: a header comment naming each clashing field
   * with theirs for reference, then a YAML body of the auto-merge with clashing fields defaulting
   * to yours. Both sides must be present (delete-vs-edit has no field merge).
   */
  public static String mergeTemplate(
      Map<String, Object> base,
      Map<String, Object> mine,
      Map<String, Object> theirs,
      List<String> clashingFields) {
    var safeBase = base == null ? Map.<String, Object>of() : base;
    var merged = new java.util.LinkedHashMap<String, Object>();
    for (var field : allKeys(safeBase, mine, theirs)) {
      var atBase = safeBase.get(field);
      var atMine = value(mine, field);
      var atTheirs = value(theirs, field);
      if (clashingFields.contains(field) || !Objects.equals(atBase, atTheirs)) {
        merged.put(field, clashingFields.contains(field) ? atMine : atTheirs);
      } else {
        merged.put(field, atMine);
      }
    }

    var header = new StringBuilder();
    header.append("# Resolve this conflict, then save and close the editor.\n");
    header.append("# Disjoint edits are already merged below; the value shown is YOURS.\n");
    for (var field : clashingFields) {
      header
          .append("#   ")
          .append(field)
          .append(": theirs = ")
          .append(render(value(theirs, field)))
          .append('\n');
    }
    return header + YamlUtil.dumpToString(merged);
  }

  /** Parses an edited merge template back into a comparable snapshot. */
  public static Map<String, Object> parseTemplate(String edited) {
    return YamlUtil.parseMap(edited);
  }

  /** A one-line human rendering of a snapshot value for diffs and headers. */
  public static String render(Object value) {
    return switch (value) {
      case null -> "∅";
      case List<?> list ->
          list.isEmpty() ? "[]" : String.join(", ", list.stream().map(String::valueOf).toList());
      default -> value.toString();
    };
  }

  private static Object value(Map<String, Object> map, String field) {
    return map == null ? null : map.get(field);
  }

  @SafeVarargs
  private static LinkedHashSet<String> allKeys(Map<String, Object>... maps) {
    var keys = new LinkedHashSet<String>();
    for (var map : maps) {
      if (map != null) {
        keys.addAll(map.keySet());
      }
    }
    return keys;
  }
}
