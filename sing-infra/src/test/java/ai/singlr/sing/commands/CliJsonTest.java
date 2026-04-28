/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class CliJsonTest {

  @Test
  void serializesRecordFieldsAsSnakeCase() {
    var json = CliJson.stringify(new ExampleResponse("demo", "spec-a", true, List.of("one")));

    assertEquals(
        "{\"project_name\": \"demo\", \"spec_id\": \"spec-a\", \"dry_run\": true, \"lines\": [\"one\"]}",
        json);
  }

  @Test
  void omitsNullRecordFields() {
    var json = CliJson.stringify(new OptionalResponse("demo", null));

    assertEquals("{\"name\": \"demo\"}", json);
  }

  @Test
  void escapesStrings() {
    var json = CliJson.stringify(new OptionalResponse("demo\n\"quoted\"", "tab\tvalue"));

    assertEquals("{\"name\": \"demo\\n\\\"quoted\\\"\", \"value\": \"tab\\tvalue\"}", json);
  }

  private record ExampleResponse(
      String projectName, String specId, boolean dryRun, List<String> lines) {}

  private record OptionalResponse(String name, String value) {}
}
