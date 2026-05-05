/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpecTest {

  @Test
  void parsesCompleteSpec() {
    var map =
        Map.<String, Object>of(
            "id", "oauth-flow",
            "title", "Implement OAuth",
            "status", "in_progress",
            "assignee", "alice",
            "depends_on", List.of("setup-db"),
            "branch", "feat/oauth");

    var spec = Spec.fromMap(map);

    assertEquals("oauth-flow", spec.id());
    assertEquals("Implement OAuth", spec.title());
    assertEquals("in_progress", spec.status());
    assertEquals("alice", spec.assignee());
    assertEquals(List.of("setup-db"), spec.dependsOn());
    assertEquals("feat/oauth", spec.branch());
  }

  @Test
  void defaultsStatusToPending() {
    var spec = Spec.fromMap(Map.of("id", "task1"));

    assertEquals("pending", spec.status());
  }

  @Test
  void defaultsTitleToEmpty() {
    var spec = Spec.fromMap(Map.of("id", "task1"));

    assertEquals("", spec.title());
  }

  @Test
  void defaultsDependsOnToEmptyList() {
    var spec = Spec.fromMap(Map.of("id", "task1"));

    assertTrue(spec.dependsOn().isEmpty());
  }

  @Test
  void nullableFieldsAreNull() {
    var spec = Spec.fromMap(Map.of("id", "task1"));

    assertNull(spec.assignee());
    assertNull(spec.branch());
  }

  @Test
  void throwsOnMissingId() {
    assertThrows(IllegalArgumentException.class, () -> Spec.fromMap(Map.of("title", "No ID")));
  }

  @Test
  void throwsOnBlankId() {
    assertThrows(
        IllegalArgumentException.class, () -> Spec.fromMap(Map.of("id", "  ", "title", "Blank")));
  }

  @Test
  void throwsOnNullId() {
    var map = new HashMap<String, Object>();
    map.put("id", null);
    assertThrows(IllegalArgumentException.class, () -> Spec.fromMap(map));
  }

  @Test
  void toMapContainsAllFields() {
    var spec = new Spec("auth", "Implement Auth", "done", "bob", List.of("setup"), "feat/auth");

    var map = spec.toMap();

    assertEquals("auth", map.get("id"));
    assertEquals("Implement Auth", map.get("title"));
    assertEquals("done", map.get("status"));
    assertEquals("bob", map.get("assignee"));
    assertEquals(List.of("setup"), map.get("depends_on"));
    assertEquals("feat/auth", map.get("branch"));
  }

  @Test
  void toMapOmitsNullAndEmptyFields() {
    var spec = new Spec("auth", "", "pending", null, List.of(), null);

    var map = spec.toMap();

    assertEquals("auth", map.get("id"));
    assertEquals("pending", map.get("status"));
    assertFalse(map.containsKey("title"));
    assertFalse(map.containsKey("assignee"));
    assertFalse(map.containsKey("depends_on"));
    assertFalse(map.containsKey("branch"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void roundTrips() {
    var spec = new Spec("auth", "Implement Auth", "review", "alice", List.of("db"), "feat/auth");

    var parsed = Spec.fromMap(spec.toMap());

    assertEquals(spec.id(), parsed.id());
    assertEquals(spec.title(), parsed.title());
    assertEquals(spec.status(), parsed.status());
    assertEquals(spec.assignee(), parsed.assignee());
    assertEquals(spec.dependsOn(), parsed.dependsOn());
    assertEquals(spec.branch(), parsed.branch());
  }

  @Test
  void dependsOnListIsImmutable() {
    var map = new HashMap<String, Object>();
    map.put("id", "test");
    map.put("depends_on", new java.util.ArrayList<>(List.of("a", "b")));

    var spec = Spec.fromMap(map);

    assertThrows(UnsupportedOperationException.class, () -> spec.dependsOn().add("c"));
  }

  @Test
  void multipleDependencies() {
    var spec =
        Spec.fromMap(Map.of("id", "final", "depends_on", List.of("step1", "step2", "step3")));

    assertEquals(3, spec.dependsOn().size());
    assertEquals(List.of("step1", "step2", "step3"), spec.dependsOn());
  }
}
