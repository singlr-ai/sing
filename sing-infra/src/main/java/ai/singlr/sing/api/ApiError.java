/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.api;

import java.util.List;

public record ApiError(String code, String message, String action, List<FieldError> fieldErrors) {
  public ApiError(String code, String message, String action) {
    this(code, message, action, List.of());
  }

  public ApiError {
    action = action == null || action.isBlank() ? null : action;
    fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
  }

  static ApiError from(Result.Failure<?> failure) {
    return new ApiError(
        failure.errorCode().code(),
        failure.errorMessage(),
        failure.action(),
        failure.fieldErrors());
  }
}
