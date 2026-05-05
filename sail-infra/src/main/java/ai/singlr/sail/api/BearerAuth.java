/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.api;

import com.sun.net.httpserver.HttpExchange;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class BearerAuth {

  private final byte[] expected;

  public BearerAuth(String token) {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("API token must not be blank.");
    }
    expected = token.getBytes(StandardCharsets.UTF_8);
  }

  public void require(HttpExchange exchange) {
    var headers = exchange.getRequestHeaders().get("Authorization");
    if (headers != null && headers.size() != 1) {
      throw new ApiException(ErrorCode.INVALID_BEARER_TOKEN, "Bearer token is invalid.");
    }
    var header = exchange.getRequestHeaders().getFirst("Authorization");
    if (header == null || !header.startsWith("Bearer ")) {
      throw new ApiException(
          ErrorCode.MISSING_BEARER_TOKEN,
          "Missing bearer token.",
          "Send Authorization: Bearer <token>.");
    }
    var actual = header.substring("Bearer ".length()).getBytes(StandardCharsets.UTF_8);
    if (!MessageDigest.isEqual(expected, actual)) {
      throw new ApiException(ErrorCode.INVALID_BEARER_TOKEN, "Bearer token is invalid.");
    }
  }
}
