/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.api;

import com.sun.net.httpserver.HttpExchange;

@FunctionalInterface
public interface ApiAuth {
  void require(HttpExchange exchange);
}
