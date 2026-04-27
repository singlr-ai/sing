/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.api;

public record FieldError(String field, String message) {
  public static FieldError of(String field, String message) {
    return new FieldError(field, message);
  }
}
