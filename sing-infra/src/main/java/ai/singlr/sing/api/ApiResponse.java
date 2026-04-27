/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.api;

import java.util.LinkedHashMap;
import java.util.Map;

public record ApiResponse(int status, Map<String, Object> body) {

  public static ApiResponse ok(Map<String, Object> body) {
    return new ApiResponse(200, withSchema(body));
  }

  public static ApiResponse created(Map<String, Object> body) {
    return new ApiResponse(201, withSchema(body));
  }

  public static ApiResponse error(int status, ApiError error) {
    var body = new LinkedHashMap<String, Object>();
    body.put("schema_version", 1);
    body.put("error", error.toMap());
    return new ApiResponse(status, body);
  }

  private static Map<String, Object> withSchema(Map<String, Object> source) {
    var body = new LinkedHashMap<String, Object>();
    body.put("schema_version", 1);
    body.putAll(source);
    return body;
  }
}
