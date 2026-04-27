/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.api;

import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public sealed interface Result<T> permits Result.Success, Result.Failure {

  record Success<T>(T value, int code) implements Result<T> {}

  record Failure<T>(
      ErrorCode errorCode,
      String errorMessage,
      String action,
      List<FieldError> fieldErrors,
      Throwable cause)
      implements Result<T> {
    public Failure {
      fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public String fullError() {
      if (errorMessage == null) {
        return null;
      }
      if (fieldErrors.isEmpty()) {
        return errorMessage;
      }
      var details =
          fieldErrors.stream()
              .map(fieldError -> fieldError.field() + ": " + fieldError.message())
              .collect(Collectors.joining(", "));
      return errorMessage + " - Field errors: [" + details + "]";
    }
  }

  default boolean isSuccess() {
    return this instanceof Success<?>;
  }

  default boolean isFailure() {
    return this instanceof Failure<?>;
  }

  default T orThrow() {
    if (this instanceof Success<?>) {
      return ((Success<T>) this).value();
    }
    var failure = (Failure<T>) this;
    throw new IllegalStateException(failure.fullError(), failure.cause());
  }

  default T value() {
    return null;
  }

  default int code() {
    var failure = (Failure<T>) this;
    return failure.errorCode().httpCode();
  }

  default ErrorCode errorCode() {
    return null;
  }

  default String errorMessage() {
    return null;
  }

  default String action() {
    return null;
  }

  default List<FieldError> fieldErrors() {
    return List.of();
  }

  default Throwable cause() {
    return null;
  }

  default <U> Result<U> asFailure() {
    if (this instanceof Failure<?>) {
      var failure = (Failure<T>) this;
      return new Failure<>(
          failure.errorCode(),
          failure.errorMessage(),
          failure.action(),
          failure.fieldErrors(),
          failure.cause());
    }
    throw new IllegalStateException("Cannot convert a success result to a failure");
  }

  default String fullError() {
    return null;
  }

  static <T> Result<T> success(T value) {
    return new Success<>(value, 200);
  }

  static <T> Result<T> success(T value, int code) {
    return new Success<>(value, code);
  }

  static <T> Result<T> failure(ErrorCode errorCode, String errorMessage) {
    return new Failure<>(errorCode, errorMessage, null, List.of(), null);
  }

  static <T> Result<T> failure(ErrorCode errorCode, String errorMessage, String action) {
    return new Failure<>(errorCode, errorMessage, action, List.of(), null);
  }

  static <T> Result<T> failure(ErrorCode errorCode, String errorMessage, Throwable cause) {
    return new Failure<>(errorCode, errorMessage, null, List.of(), cause);
  }

  static <T> Result<T> failure(
      ErrorCode errorCode, String errorMessage, List<FieldError> fieldErrors) {
    return new Failure<>(errorCode, errorMessage, null, fieldErrors, null);
  }
}
