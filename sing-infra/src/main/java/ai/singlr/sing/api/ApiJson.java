/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.api;

import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ApiJson {

  private ApiJson() {}

  static Map<String, Object> withSchema(Object value) {
    var map = new LinkedHashMap<String, Object>();
    map.put("schema_version", 1);
    var encoded = encode(value);
    if (encoded instanceof Map<?, ?> encodedMap) {
      for (var entry : encodedMap.entrySet()) {
        map.put(entry.getKey().toString(), entry.getValue());
      }
      return map;
    }
    map.put("data", encoded);
    return map;
  }

  @SuppressWarnings("unchecked")
  static Object encode(Object value) {
    return switch (value) {
      case null -> null;
      case String ignored -> value;
      case Number ignored -> value;
      case Boolean ignored -> value;
      case Enum<?> enumValue -> enumValue.name().toLowerCase();
      case List<?> list -> list.stream().map(ApiJson::encode).toList();
      case Map<?, ?> map -> encodeMap((Map<Object, Object>) map);
      default -> value.getClass().isRecord() ? encodeRecord(value) : value.toString();
    };
  }

  private static Map<String, Object> encodeMap(Map<Object, Object> source) {
    var map = new LinkedHashMap<String, Object>();
    for (var entry : source.entrySet()) {
      var value = encode(entry.getValue());
      if (value != null) {
        map.put(entry.getKey().toString(), value);
      }
    }
    return map;
  }

  private static Map<String, Object> encodeRecord(Object record) {
    var map = new LinkedHashMap<String, Object>();
    for (var component : record.getClass().getRecordComponents()) {
      var value = componentValue(record, component);
      if (value != null) {
        map.put(toSnakeCase(component.getName()), encode(value));
      }
    }
    return map;
  }

  private static Object componentValue(Object record, RecordComponent component) {
    try {
      return component.getAccessor().invoke(record);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to serialize API response", e);
    }
  }

  private static String toSnakeCase(String name) {
    var builder = new StringBuilder();
    for (var i = 0; i < name.length(); i++) {
      var c = name.charAt(i);
      if (Character.isUpperCase(c)) {
        builder.append('_').append(Character.toLowerCase(c));
      } else {
        builder.append(c);
      }
    }
    return builder.toString();
  }
}
