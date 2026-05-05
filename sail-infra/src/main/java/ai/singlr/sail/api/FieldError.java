/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.api;

public record FieldError(String field, String message) {
  public static FieldError of(String field, String message) {
    return new FieldError(field, message);
  }
}
