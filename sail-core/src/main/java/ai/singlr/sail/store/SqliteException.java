/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.store;

public final class SqliteException extends RuntimeException {

  private final int code;

  public SqliteException(String message, int code) {
    super(message + " (sqlite error " + code + ")");
    this.code = code;
  }

  public SqliteException(String message, Throwable cause) {
    super(message, cause);
    this.code = -1;
  }

  public int code() {
    return code;
  }
}
