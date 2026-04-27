/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.api;

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
    var header = exchange.getRequestHeaders().getFirst("Authorization");
    if (header == null || !header.startsWith("Bearer ")) {
      throw new ApiException(
          401,
          "missing_bearer_token",
          "Missing bearer token.",
          "Send Authorization: Bearer <token>.");
    }
    var actual = header.substring("Bearer ".length()).getBytes(StandardCharsets.UTF_8);
    if (!MessageDigest.isEqual(expected, actual)) {
      throw new ApiException(403, "invalid_bearer_token", "Bearer token is invalid.", null);
    }
  }
}
