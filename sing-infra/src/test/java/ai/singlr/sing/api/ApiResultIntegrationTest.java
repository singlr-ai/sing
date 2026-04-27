/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiResultIntegrationTest {

  @Test
  void exceptionExposesFailureStatusAndError() {
    var exception = new ApiException(ErrorCode.PROJECT_STOPPED, "Stopped.", "Run sing up.");

    assertEquals(409, exception.status());
    assertEquals(ErrorCode.PROJECT_STOPPED, exception.failure().errorCode());
    assertEquals("project_stopped", exception.error().code());
    assertEquals("Run sing up.", exception.error().action());
  }

  @Test
  void exceptionSupportsCauses() {
    var cause = new IllegalStateException("root");
    var exception = new ApiException(ErrorCode.COMMAND_FAILED, "Failed.", cause);

    assertEquals(cause, exception.failure().cause());
  }

  @Test
  void responseHelpersSerializeSuccessAndFailureResults() {
    var success = ApiResponse.ok(Map.of("ok", true));
    var failure = ApiResponse.from(Result.failure(ErrorCode.NOT_FOUND, "Missing."));

    assertEquals(200, success.status());
    assertEquals(true, success.body().get("ok"));
    assertEquals(404, failure.status());
    assertEquals("not_found", failure.body().toString().contains("not_found") ? "not_found" : null);
  }
}
