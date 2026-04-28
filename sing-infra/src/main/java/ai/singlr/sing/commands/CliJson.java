/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;

final class CliJson {

  private CliJson() {}

  static String stringify(Object value) {
    var builder = new StringBuilder();
    append(builder, value);
    return builder.toString();
  }

  @SuppressWarnings("unchecked")
  private static void append(StringBuilder builder, Object value) {
    switch (value) {
      case null -> builder.append("null");
      case String string -> appendString(builder, string);
      case Number number -> builder.append(number);
      case Boolean bool -> builder.append(bool);
      case Enum<?> enumValue -> appendString(builder, enumValue.name().toLowerCase());
      case List<?> list -> appendList(builder, list);
      case Map<?, ?> map -> appendMap(builder, (Map<Object, Object>) map);
      default -> {
        if (value.getClass().isRecord()) {
          appendRecord(builder, value);
        } else {
          appendString(builder, value.toString());
        }
      }
    }
  }

  private static void appendList(StringBuilder builder, List<?> values) {
    builder.append('[');
    for (var index = 0; index < values.size(); index++) {
      if (index > 0) {
        builder.append(", ");
      }
      append(builder, values.get(index));
    }
    builder.append(']');
  }

  private static void appendMap(StringBuilder builder, Map<Object, Object> values) {
    builder.append('{');
    var first = true;
    for (var entry : values.entrySet()) {
      if (entry.getValue() == null) {
        continue;
      }
      if (!first) {
        builder.append(", ");
      }
      appendString(builder, entry.getKey().toString());
      builder.append(": ");
      append(builder, entry.getValue());
      first = false;
    }
    builder.append('}');
  }

  private static void appendRecord(StringBuilder builder, Object record) {
    builder.append('{');
    var first = true;
    for (var component : record.getClass().getRecordComponents()) {
      var value = componentValue(record, component);
      if (value == null) {
        continue;
      }
      if (!first) {
        builder.append(", ");
      }
      appendString(builder, toSnakeCase(component.getName()));
      builder.append(": ");
      append(builder, value);
      first = false;
    }
    builder.append('}');
  }

  private static Object componentValue(Object record, RecordComponent component) {
    try {
      return component.getAccessor().invoke(record);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to serialize CLI JSON response", e);
    }
  }

  private static void appendString(StringBuilder builder, String value) {
    builder.append('"');
    for (var index = 0; index < value.length(); index++) {
      var ch = value.charAt(index);
      switch (ch) {
        case '\\' -> builder.append("\\\\");
        case '"' -> builder.append("\\\"");
        case '\b' -> builder.append("\\b");
        case '\f' -> builder.append("\\f");
        case '\n' -> builder.append("\\n");
        case '\r' -> builder.append("\\r");
        case '\t' -> builder.append("\\t");
        default -> {
          if (ch < 0x20) {
            builder.append(String.format("\\u%04x", (int) ch));
          } else {
            builder.append(ch);
          }
        }
      }
    }
    builder.append('"');
  }

  private static String toSnakeCase(String name) {
    var builder = new StringBuilder();
    for (var index = 0; index < name.length(); index++) {
      var ch = name.charAt(index);
      if (Character.isUpperCase(ch)) {
        builder.append('_').append(Character.toLowerCase(ch));
      } else {
        builder.append(ch);
      }
    }
    return builder.toString();
  }
}
