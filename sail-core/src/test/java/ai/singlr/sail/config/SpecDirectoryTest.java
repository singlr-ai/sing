/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpecDirectoryTest {

  @Test
  void parseMetadataReturnsSpec() {
    var metadata =
        Map.<String, Object>of(
            "id",
            "search",
            "title",
            "Add search",
            "status",
            "pending",
            "depends_on",
            List.of("auth"));

    var spec = SpecDirectory.parseMetadata(metadata);

    assertEquals("search", spec.id());
    assertEquals("Add search", spec.title());
    assertEquals("pending", spec.status());
    assertEquals(List.of("auth"), spec.dependsOn());
  }

  @Test
  void generateMetadataRoundTrips() {
    var spec = new Spec("search", "Search", "pending", "alice", List.of("auth"), "feat/search");

    var metadata = SpecDirectory.generateMetadata(spec);
    var parsed = SpecDirectory.parseMetadata(metadata);

    assertEquals("search", parsed.id());
    assertEquals("alice", parsed.assignee());
    assertEquals(List.of("auth"), parsed.dependsOn());
    assertEquals("feat/search", parsed.branch());
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
  void nextReadyCombinesAssigneeAndDependencyFiltering() {
    var specs =
        List.of(
            new Spec("setup", "Setup", "done", null, List.of(), null),
            new Spec("alice-dep", "Alice dep", "pending", "alice", List.of("setup"), null),
            new Spec("bob-nodep", "Bob nodep", "pending", "bob", List.of(), null));

    assertEquals("alice-dep", SpecDirectory.nextReady(specs, "alice").id());
    assertEquals("bob-nodep", SpecDirectory.nextReady(specs, "bob").id());
  }

  @Test
  void findByIdReturnsMatchingSpec() {
    var specs = List.of(new Spec("oauth-flow", "OAuth", "pending", null, List.of(), null));

    var spec = SpecDirectory.findById(specs, "oauth-flow");

    assertNotNull(spec);
    assertEquals("OAuth", spec.title());
  }

  @Test
  void updateStatusReplacesOnlyMatchingSpec() {
    var specs =
        List.of(
            new Spec("auth", "Auth", "pending", null, List.of(), null),
            new Spec("search", "Search", "pending", null, List.of(), null));

    var updated = SpecDirectory.updateStatus(specs, "search", "review");

    assertEquals("pending", updated.getFirst().status());
    assertEquals("review", updated.get(1).status());
  }

  @Test
  void updateStatusRejectsUnknownStatus() {
    var specs = List.of(new Spec("auth", "Auth", "pending", null, List.of(), null));

    var error =
        assertThrows(
            IllegalArgumentException.class,
            () -> SpecDirectory.updateStatus(specs, "auth", "blocked"));

    assertTrue(error.getMessage().contains("Invalid spec status"));
  }

  @Test
  void isReadyReturnsTrueForPendingSpecWithSatisfiedDependencies() {
    var specs =
        List.of(
            new Spec("setup", "Setup", "done", null, List.of(), null),
            new Spec("oauth", "OAuth", "pending", null, List.of("setup"), null));

    assertTrue(SpecDirectory.isReady(specs, specs.get(1)));
    assertFalse(SpecDirectory.isBlocked(specs, specs.get(1)));
  }

  @Test
  void isBlockedReturnsTrueForPendingSpecWithUnmetDependencies() {
    var specs =
        List.of(
            new Spec("setup", "Setup", "in_progress", null, List.of(), null),
            new Spec("oauth", "OAuth", "pending", null, List.of("setup"), null));

    assertTrue(SpecDirectory.isBlocked(specs, specs.get(1)));
    assertEquals(List.of("setup"), SpecDirectory.unmetDependencies(specs, specs.get(1)));
  }

  @Test
  void summarizeReportsReadyAndBlockedCounts() {
    var specs =
        List.of(
            new Spec("setup", "Setup", "done", null, List.of(), null),
            new Spec("ready", "Ready", "pending", null, List.of("setup"), null),
            new Spec("blocked", "Blocked", "pending", null, List.of("missing"), null),
            new Spec("review", "Review", "review", null, List.of(), null));

    var summary = SpecDirectory.summarize(specs);

    assertEquals(1, summary.readyCount());
    assertEquals(1, summary.blockedCount());
    assertEquals("ready", summary.nextReadyId());
    assertEquals(2, summary.counts().get("pending"));
  }
}
