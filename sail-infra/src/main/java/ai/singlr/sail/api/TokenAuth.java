/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.api;

import ai.singlr.sail.store.TokenStore;
import com.sun.net.httpserver.HttpExchange;

/**
 * Validates bearer tokens against the SQLite-backed {@link TokenStore}. Replaces the static
 * single-token {@link BearerAuth} for the control plane server.
 */
public final class TokenAuth implements ApiAuth {

  private final TokenStore tokenStore;

  public TokenAuth(TokenStore tokenStore) {
    this.tokenStore = tokenStore;
  }

  public void require(HttpExchange exchange) {
    var header = exchange.getRequestHeaders().getFirst("Authorization");
    if (header == null || !header.startsWith("Bearer ")) {
      throw new ApiException(
          ErrorCode.MISSING_BEARER_TOKEN,
          "Missing bearer token.",
          "Send Authorization: Bearer <token>.");
    }
    var token = header.substring("Bearer ".length());
    var info = tokenStore.validate(token);
    if (info.isEmpty()) {
      throw new ApiException(ErrorCode.INVALID_BEARER_TOKEN, "Bearer token is invalid.");
    }
    exchange.setAttribute("token.name", info.get().name());
    exchange.setAttribute("token.role", info.get().role());
  }
}
