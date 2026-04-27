/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiJsonTest {

  @Test
  void schemaWrapsScalarValuesAsData() {
    var json = ApiJson.withSchema("ok");

    assertEquals(1, json.get("schema_version"));
    assertEquals("ok", json.get("data"));
  }

  @Test
  void encodesPrimitiveNullEnumListAndMapValues() {
    var value =
        Map.of(
            "text",
            "ok",
            "number",
            7,
            "flag",
            true,
            "error",
            ErrorCode.NOT_FOUND,
            "list",
            List.of("one", ErrorCode.CONFLICT));

    var encoded = ApiJson.encode(value);

    assertTrue(encoded instanceof Map<?, ?>);
    var map = (Map<?, ?>) encoded;
    assertEquals("ok", map.get("text"));
    assertEquals(7, map.get("number"));
    assertEquals(true, map.get("flag"));
    assertEquals("not_found", map.get("error"));
    assertEquals(List.of("one", "conflict"), map.get("list"));
    assertNull(ApiJson.encode(null));
  }

  @Test
  void omitsNullMapAndRecordValues() {
    var source = new java.util.LinkedHashMap<String, Object>();
    source.put("keep", "yes");
    source.put("skip", null);
    var encodedMap = ApiJson.encode(source);
    var record = new SampleRecord("hello", null, ErrorCode.INTERNAL);
    var encodedRecord = ApiJson.encode(record);

    assertEquals(Map.of("keep", "yes"), encodedMap);
    assertTrue(encodedRecord instanceof Map<?, ?>);
    var map = (Map<?, ?>) encodedRecord;
    assertEquals("hello", map.get("camel_case"));
    assertEquals("internal", map.get("error_code"));
    assertFalse(map.containsKey("missing_value"));
  }

  @Test
  void fallsBackToStringForPlainObjects() {
    var value = new StringBuilder("plain");

    assertEquals("plain", ApiJson.encode(value));
  }

  @Test
  void wrapsRecordAccessorFailures() {
    var record = new ExplodingRecord("value");

    var thrown = assertThrows(IllegalStateException.class, () -> ApiJson.encode(record));

    assertEquals("Failed to serialize API response", thrown.getMessage());
  }

  private record SampleRecord(String camelCase, String missingValue, ErrorCode errorCode) {}

  private record ExplodingRecord(String value) {
    @Override
    public String value() {
      throw new IllegalStateException("boom");
    }
  }
}
