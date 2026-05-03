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
import java.util.Objects;

public final class JsonBody {

  private static final int MAX_BYTES = 64 * 1024;

  private JsonBody() {}

  public static DispatchRequest readDispatchRequest(HttpExchange exchange) throws IOException {
    var map = read(exchange);
    return new DispatchRequest(
        optionalString(map, "spec_id"),
        optionalString(map, "mode"),
        Boolean.TRUE.equals(map.get("dry_run")));
  }

  public static SpecSyncRequest readSpecSyncRequest(HttpExchange exchange) throws IOException {
    var map = read(exchange);
    return new SpecSyncRequest(
        optionalString(map, "operation"),
        optionalString(map, "remote"),
        optionalString(map, "branch"));
  }

  private static Map<String, Object> read(HttpExchange exchange) throws IOException {
    var contentType = exchange.getRequestHeaders().getFirst("Content-Type");
    if (contentType != null && !contentType.toLowerCase().startsWith("application/json")) {
      throw new ApiException(
          ErrorCode.UNSUPPORTED_MEDIA_TYPE,
          "Requests with a body must use application/json.",
          "Set Content-Type to application/json.");
    }
    var bytes = exchange.getRequestBody().readNBytes(MAX_BYTES + 1);
    if (bytes.length > MAX_BYTES) {
      throw new ApiException(
          ErrorCode.REQUEST_TOO_LARGE,
          "Request body exceeds 65536 bytes.",
          "Send a smaller JSON body.");
    }
    if (bytes.length == 0) {
      return Map.of();
    }
    try {
      return YamlUtil.parseMap(new String(bytes, StandardCharsets.UTF_8));
    } catch (RuntimeException e) {
      throw new ApiException(ErrorCode.INVALID_JSON, "Request body is not valid JSON.", e);
    }
  }

  private static String optionalString(Map<String, Object> map, String key) {
    var value = map.get(key);
    return value == null ? null : Objects.toString(value);
  }
}
