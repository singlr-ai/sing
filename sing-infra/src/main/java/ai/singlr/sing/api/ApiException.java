/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.api;

public final class ApiException extends RuntimeException {

  private final int status;
  private final ApiError error;

  public ApiException(int status, String code, String message, String action) {
    super(message);
    this.status = status;
    this.error = new ApiError(code, message, action);
  }

  public int status() {
    return status;
  }

  public ApiError error() {
    return error;
  }
}
