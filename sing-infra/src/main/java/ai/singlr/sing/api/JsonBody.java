/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.api;

import ai.singlr.sing.config.YamlUtil;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class JsonBody {

  private static final int MAX_BYTES = 64 * 1024;

  private JsonBody() {}

  public static Map<String, Object> read(HttpExchange exchange) throws IOException {
    var contentType = exchange.getRequestHeaders().getFirst("Content-Type");
    if (contentType != null && !contentType.toLowerCase().startsWith("application/json")) {
      throw new ApiException(
          415,
          "unsupported_media_type",
          "Requests with a body must use application/json.",
          "Set Content-Type to application/json.");
    }
    var bytes = exchange.getRequestBody().readNBytes(MAX_BYTES + 1);
    if (bytes.length > MAX_BYTES) {
      throw new ApiException(
          413,
          "request_too_large",
          "Request body exceeds 65536 bytes.",
          "Send a smaller JSON body.");
    }
    if (bytes.length == 0) {
      return Map.of();
    }
    try {
      return YamlUtil.parseMap(new String(bytes, StandardCharsets.UTF_8));
    } catch (RuntimeException e) {
      throw new ApiException(400, "invalid_json", "Request body is not valid JSON.", null);
    }
  }
}
