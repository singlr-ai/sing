/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Lifecycle state of a {@link Spec}. The single source of truth for the spec status vocabulary —
 * persisted, serialized, and compared as its {@link #wire()} form (the lowercased name, e.g. {@code
 * IN_PROGRESS} → {@code "in_progress"}).
 *
 * <p>{@link #AWAITING_MERGE} sits between the review gate and completion: the review passed, the
 * pull request is open, and a human still has to merge it on the forge and mark the spec {@link
 * #DONE}. {@link #DRAFT} is the bucket for imported specs whose status is unknown or absent —
 * including a status synced from a newer peer that this binary does not know yet, which lands in
 * the draft column instead of crashing; {@link #ARCHIVED} is hidden from the default board. Only
 * {@link SpecDirectory#CLI_SETTABLE} statuses may be assigned by hand via {@code sail spec status}.
 */
public enum SpecStatus {
  DRAFT,
  PENDING,
  IN_PROGRESS,
  REVIEW,
  AWAITING_MERGE,
  DONE,
  ARCHIVED;

  /** The persisted/serialized form: the lowercased enum name. */
  public String wire() {
    return name().toLowerCase(Locale.ROOT);
  }

  /**
   * Whether a spec in this status may have its owner changed freely. Only pre-dispatch statuses
   * ({@link #DRAFT}, {@link #PENDING}) qualify; once a spec is dispatched its claim is locked, and
   * reassignment requires an explicit force override.
   */
  public boolean isReassignable() {
    return this == DRAFT || this == PENDING;
  }

  /** Parses a wire-form status, rejecting anything outside the vocabulary. */
  public static SpecStatus fromWire(String value) {
    var match = byWire(value);
    if (match == null) {
      throw new IllegalArgumentException(
          "Invalid spec status: '" + value + "'. Must be one of: " + wireValues());
    }
    return match;
  }

  /**
   * Lenient parse for importing pre-control-plane file specs: a {@code null}/blank or unrecognized
   * status falls back to {@link #DRAFT}, and the legacy {@code "archive"} alias maps to {@link
   * #ARCHIVED}.
   */
  public static SpecStatus fromLegacy(String value) {
    if (value == null) {
      return DRAFT;
    }
    var match = byWire(value);
    if (match != null) {
      return match;
    }
    return "archive".equals(value) ? ARCHIVED : DRAFT;
  }

  /** Comma-separated wire forms, for error messages and listings. */
  public static String wireValues() {
    return Arrays.stream(values()).map(SpecStatus::wire).collect(Collectors.joining(", "));
  }

  private static SpecStatus byWire(String value) {
    for (var status : values()) {
      if (status.wire().equals(value)) {
        return status;
      }
    }
    return null;
  }
}
