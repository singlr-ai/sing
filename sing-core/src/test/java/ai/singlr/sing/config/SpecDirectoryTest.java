/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpecDirectoryTest {

  @Test
  void parseIndexReturnsOrderedSpecs() {
    var indexMap =
        Map.<String, Object>of(
            "specs",
            List.of(
                Map.<String, Object>of("id", "auth", "title", "Implement auth", "status", "done"),
                Map.<String, Object>of(
                    "id", "search",
                    "title", "Add search",
                    "status", "pending",
                    "depends_on", List.of("auth"))));

    var specs = SpecDirectory.parseIndex(indexMap);

    assertEquals(2, specs.size());
    assertEquals("auth", specs.getFirst().id());
    assertEquals("search", specs.get(1).id());
    assertEquals(List.of("auth"), specs.get(1).dependsOn());
  }

  @Test
  void parseIndexReturnsEmptyForMissingKey() {
    assertTrue(SpecDirectory.parseIndex(Map.of()).isEmpty());
  }

  @Test
  void parseIndexReturnsEmptyForEmptyList() {
    assertTrue(SpecDirectory.parseIndex(Map.of("specs", List.of())).isEmpty());
  }

  @Test
  @SuppressWarnings("unchecked")
  void generateIndexRoundTrips() {
    var specs =
        List.of(
            new Spec("auth", "Auth", "done", null, List.of(), null),
            new Spec("search", "Search", "pending", "alice", List.of("auth"), "feat/search"));

    var indexMap = SpecDirectory.generateIndex(specs);
    var parsed = SpecDirectory.parseIndex(indexMap);

    assertEquals(2, parsed.size());
    assertEquals("auth", parsed.getFirst().id());
    assertEquals("done", parsed.getFirst().status());
    assertEquals("search", parsed.get(1).id());
    assertEquals("alice", parsed.get(1).assignee());
    assertEquals(List.of("auth"), parsed.get(1).dependsOn());
  }

  @Test
  void nextReadyReturnsFirstPending() {
    var specs =
        List.of(
            new Spec("done-spec", "Done", "done", null, List.of(), null),
            new Spec("ready", "Ready", "pending", null, List.of(), null));

    var next = SpecDirectory.nextReady(specs);

    assertNotNull(next);
    assertEquals("ready", next.id());
  }

  @Test
  void nextReadyRespectsDependencies() {
    var specs =
        List.of(
            new Spec("first", "First", "pending", null, List.of(), null),
            new Spec("second", "Second", "pending", null, List.of("first"), null));

    var next = SpecDirectory.nextReady(specs);

    assertEquals("first", next.id());
  }

  @Test
  void nextReadySkipsBlockedDependency() {
    var specs =
        List.of(
            new Spec("first", "First", "in_progress", null, List.of(), null),
            new Spec("second", "Second", "pending", null, List.of("first"), null),
            new Spec("third", "Third", "pending", null, List.of(), null));

    var next = SpecDirectory.nextReady(specs);

    assertEquals("third", next.id());
  }

  @Test
  void nextReadyReturnsDependentWhenDepDone() {
    var specs =
        List.of(
            new Spec("first", "First", "done", null, List.of(), null),
            new Spec("second", "Second", "pending", null, List.of("first"), null));

    var next = SpecDirectory.nextReady(specs);

    assertEquals("second", next.id());
  }

  @Test
  void nextReadyReturnsNullWhenAllDone() {
    var specs =
        List.of(
            new Spec("a", "A", "done", null, List.of(), null),
            new Spec("b", "B", "done", null, List.of(), null));

    assertNull(SpecDirectory.nextReady(specs));
  }

  @Test
  void nextReadyReturnsNullWhenEmpty() {
    assertNull(SpecDirectory.nextReady(List.of()));
  }

  @Test
  void nextReadySkipsInProgress() {
    var specs =
        List.of(
            new Spec("wip", "WIP", "in_progress", null, List.of(), null),
            new Spec("ready", "Ready", "pending", null, List.of(), null));

    var next = SpecDirectory.nextReady(specs);

    assertEquals("ready", next.id());
  }

  @Test
  void nextReadySkipsReviewStatus() {
    var specs =
        List.of(
            new Spec("reviewing", "Reviewing", "review", null, List.of(), null),
            new Spec("ready", "Ready", "pending", null, List.of(), null));

    var next = SpecDirectory.nextReady(specs);

    assertEquals("ready", next.id());
  }

  @Test
  void nextReadyFiltersbyAssignee() {
    var specs =
        List.of(
            new Spec("alice-task", "Alice's", "pending", "alice", List.of(), null),
            new Spec("bob-task", "Bob's", "pending", "bob", List.of(), null));

    var next = SpecDirectory.nextReady(specs, "bob");

    assertEquals("bob-task", next.id());
  }

  @Test
  void nextReadyIncludesUnassignedForAnyAssignee() {
    var specs = List.of(new Spec("unassigned", "Open", "pending", null, List.of(), null));

    var next = SpecDirectory.nextReady(specs, "alice");

    assertEquals("unassigned", next.id());
  }

  @Test
  void nextReadyNullAssigneeMatchesAll() {
    var specs = List.of(new Spec("alice-task", "Alice's", "pending", "alice", List.of(), null));

    var next = SpecDirectory.nextReady(specs, null);

    assertEquals("alice-task", next.id());
  }

  @Test
  void nextReadyReturnsNullWhenNoMatchingAssignee() {
    var specs = List.of(new Spec("alice-task", "Alice's", "pending", "alice", List.of(), null));

    assertNull(SpecDirectory.nextReady(specs, "bob"));
  }

  @Test
  void nextReadyMultipleDependenciesAllMet() {
    var specs =
        List.of(
            new Spec("a", "A", "done", null, List.of(), null),
            new Spec("b", "B", "done", null, List.of(), null),
            new Spec("c", "C", "pending", null, List.of("a", "b"), null));

    var next = SpecDirectory.nextReady(specs);

    assertEquals("c", next.id());
  }

  @Test
  void nextReadyMultipleDependenciesPartiallyMet() {
    var specs =
        List.of(
            new Spec("a", "A", "done", null, List.of(), null),
            new Spec("b", "B", "in_progress", null, List.of(), null),
            new Spec("c", "C", "pending", null, List.of("a", "b"), null));

    assertNull(SpecDirectory.nextReady(specs));
  }

  @Test
  void statusCountsAllStatuses() {
    var specs =
        List.of(
            new Spec("a", "A", "done", null, List.of(), null),
            new Spec("b", "B", "done", null, List.of(), null),
            new Spec("c", "C", "in_progress", null, List.of(), null),
            new Spec("d", "D", "pending", null, List.of(), null),
            new Spec("e", "E", "pending", null, List.of(), null),
            new Spec("f", "F", "review", null, List.of(), null));

    var counts = SpecDirectory.statusCounts(specs);

    assertEquals(2, counts.get("done"));
    assertEquals(1, counts.get("in_progress"));
    assertEquals(2, counts.get("pending"));
    assertEquals(1, counts.get("review"));
  }

  @Test
  void statusCountsEmpty() {
    var counts = SpecDirectory.statusCounts(List.of());

    assertEquals(0, counts.get("done"));
    assertEquals(0, counts.get("in_progress"));
    assertEquals(0, counts.get("pending"));
    assertEquals(0, counts.get("review"));
  }

  @Test
  void statusCountsUnknownStatusStillCounted() {
    var specs = List.of(new Spec("x", "X", "blocked", null, List.of(), null));

    var counts = SpecDirectory.statusCounts(specs);

    assertEquals(1, counts.get("blocked"));
    assertEquals(0, counts.get("pending"));
  }

  @Test
  void generateIndexPreservesOrder() {
    var specs =
        List.of(
            new Spec("z-last", "Z", "pending", null, List.of(), null),
            new Spec("a-first", "A", "done", null, List.of(), null));

    var indexMap = SpecDirectory.generateIndex(specs);
    var parsed = SpecDirectory.parseIndex(indexMap);

    assertEquals("z-last", parsed.getFirst().id());
    assertEquals("a-first", parsed.get(1).id());
  }

  @Test
  void nextReadyCombinesAssigneeAndDependencyFiltering() {
    var specs =
        List.of(
            new Spec("setup", "Setup", "done", null, List.of(), null),
            new Spec("alice-dep", "Alice dep", "pending", "alice", List.of("setup"), null),
            new Spec("bob-nodep", "Bob nodep", "pending", "bob", List.of(), null));

    assertEquals("alice-dep", SpecDirectory.nextReady(specs, "alice").id());
    assertEquals("bob-nodep", SpecDirectory.nextReady(specs, "bob").id());
  }
}
