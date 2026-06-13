/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.commands;

import static org.junit.jupiter.api.Assertions.*;

import ai.singlr.sail.config.SpecStatus;
import ai.singlr.sail.config.YamlUtil;
import ai.singlr.sail.store.SchemaManager;
import ai.singlr.sail.store.SpecStore;
import ai.singlr.sail.store.Sqlite;
import ai.singlr.sail.store.SyncConflicts;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConflictsCommandTest {

  @TempDir Path tempDir;
  private Sqlite db;
  private SpecStore specs;
  private SyncConflicts conflicts;

  @BeforeEach
  void setUp() {
    db = Sqlite.open(tempDir.resolve("test.db"));
    new SchemaManager(db).migrate();
    specs = new SpecStore(db);
    conflicts = new SyncConflicts(db);
  }

  @AfterEach
  void tearDown() {
    db.close();
  }

  private SpecStore.SpecRow spec(String id, String title) {
    return new SpecStore.SpecRow(
        id,
        "proj",
        title,
        SpecStatus.fromWire("pending"),
        null,
        null,
        null,
        null,
        null,
        0,
        "uday",
        "",
        "",
        "uday",
        List.of(),
        List.of());
  }

  private String json(Map<String, Object> map) {
    return YamlUtil.dumpJson(map);
  }

  @Test
  void renderListEmptyIsClean() {
    assertTrue(ConflictsCommand.renderList(List.of(), false).contains("No conflicts"));
  }

  @Test
  void renderListJsonCarriesEntityAndFields() {
    conflicts.record("spec", "auth", "{}", "{}", "{}", List.of("title"));
    var out = ConflictsCommand.renderList(conflicts.pending(), true);
    assertTrue(out.contains("\"entity\": \"auth\""));
    assertTrue(out.contains("\"title\""));
  }

  @Test
  void renderListHumanNamesTheConflictingEntity() {
    conflicts.record("spec", "auth", "{}", "{}", "{}", List.of("title"));
    var out = ConflictsCommand.renderList(conflicts.pending(), false);
    assertTrue(out.contains("auth"));
    assertTrue(out.contains("title"));
  }

  @Test
  void applyResolvesAndMarksTheConflict() {
    specs.create(spec("auth", "Mine"));
    var base = specs.comparableSnapshot("auth");
    var mine = new java.util.LinkedHashMap<>(base);
    mine.put("title", "Mine");
    var theirs = new java.util.LinkedHashMap<>(base);
    theirs.put("title", "Theirs");
    var id =
        conflicts.record("spec", "auth", json(base), json(mine), json(theirs), List.of("title"));
    var conflict = conflicts.pendingFor("spec", "auth").orElseThrow();

    ConflictsCommand.Resolve.apply(specs, conflicts, conflict, mine, theirs);

    assertTrue(conflicts.pending().isEmpty(), "the conflict is marked resolved");
    assertEquals("Mine", specs.findById("auth").orElseThrow().title());
    assertNotEquals(0, id);
  }

  @Test
  void strategyRequiresExactlyOneChoice() {
    assertEquals(
        ConflictsCommand.Resolve.Strategy.MINE,
        ConflictsCommand.Resolve.strategy(true, false, false));
    assertEquals(
        ConflictsCommand.Resolve.Strategy.THEIRS,
        ConflictsCommand.Resolve.strategy(false, true, false));
    assertEquals(
        ConflictsCommand.Resolve.Strategy.MERGE,
        ConflictsCommand.Resolve.strategy(false, false, true));
    assertNull(ConflictsCommand.Resolve.strategy(false, false, false));
    assertNull(ConflictsCommand.Resolve.strategy(true, true, false));
  }

  @Test
  void mergeableOnlyWhenBothSidesArePresent() {
    var both = conflicts.record("spec", "a", "{}", "{}", "{}", List.of("title"));
    assertTrue(ConflictsCommand.Resolve.mergeable(conflicts.pendingFor("spec", "a").orElseThrow()));
    assertNotEquals(0, both);

    conflicts.record("spec", "b", "{}", null, "{}", List.of("<deleted>"));
    assertFalse(
        ConflictsCommand.Resolve.mergeable(conflicts.pendingFor("spec", "b").orElseThrow()));
  }

  @Test
  void chooseSelectsTheStrategySnapshot() {
    var conflict =
        new SyncConflicts.Conflict(
            1,
            "spec",
            "auth",
            json(Map.of("title", "Base")),
            json(Map.of("title", "Mine")),
            json(Map.of("title", "Theirs")),
            List.of("title"),
            "now",
            "pending",
            null);

    assertEquals(
        "Mine",
        ConflictsCommand.Resolve.choose(conflict, ConflictsCommand.Resolve.Strategy.MINE, null)
            .get("title"));
    assertEquals(
        "Theirs",
        ConflictsCommand.Resolve.choose(conflict, ConflictsCommand.Resolve.Strategy.THEIRS, null)
            .get("title"));
    assertEquals(
        "Merged",
        ConflictsCommand.Resolve.choose(
                conflict, ConflictsCommand.Resolve.Strategy.MERGE, "title: Merged")
            .get("title"));
  }

  @Test
  void showRenderFlagsTheClashingField() {
    var conflict =
        new SyncConflicts.Conflict(
            1,
            "spec",
            "auth",
            json(Map.of("title", "Base", "status", "pending")),
            json(Map.of("title", "Mine", "status", "pending")),
            json(Map.of("title", "Theirs", "status", "in_progress")),
            List.of("title"),
            "now",
            "pending",
            null);

    var rendered = ConflictsCommand.Show.render(conflict);
    assertTrue(rendered.contains("auth"));
    assertTrue(rendered.contains("Mine"));
    assertTrue(rendered.contains("Theirs"));
    assertTrue(rendered.contains("in_progress"));
  }
}
