/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.api;

import java.util.LinkedHashMap;
import java.util.Map;

public record ApiError(String code, String message, String action) {

  public Map<String, Object> toMap() {
    var map = new LinkedHashMap<String, Object>();
    map.put("code", code);
    map.put("message", message);
    if (action != null && !action.isBlank()) {
      map.put("action", action);
    }
    return map;
  }
}
