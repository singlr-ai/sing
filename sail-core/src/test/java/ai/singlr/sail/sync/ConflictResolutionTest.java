/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.sync;

import static org.junit.jupiter.api.Assertions.*;

import ai.singlr.sail.config.SpecStatus;
import ai.singlr.sail.config.YamlUtil;
import ai.singlr.sail.store.ChangeLog;
import ai.singlr.sail.store.SchemaManager;
import ai.singlr.sail.store.SpecStore;
import ai.singlr.sail.store.Sqlite;
import ai.singlr.sail.store.SyncConflicts;
import ai.singlr.sail.store.SyncState;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The end-to-end property of {@link SpecStore#resolveConflict}: after a conflict is parked, the
 * user's choice (mine / theirs / merge) is applied, and a follow-up sync converges main and node on
 * that choice <em>without re-raising the conflict</em> — because resolving rebases the row onto
 * main's content, so detection can never see the same divergence twice. Covers field-level and
 * delete-vs-edit conflicts in both directions.
 */
class ConflictResolutionTest {

  @TempDir Path tempDir;
  private final SyncEngine engine = new SyncEngine();

  private Box main;
  private Box node;
  private Box other;

  private final class Box implements AutoCloseable {
    final Sqlite db;
    final SpecStore specs;
    final SyncConflicts conflicts;
    final SpecReplica replica;

    Box(String id) {
      this.db = Sqlite.open(tempDir.resolve(id + ".db"));
      new SchemaManager(db).migrate();
      this.specs = new SpecStore(db);
      this.conflicts = new SyncConflicts(db);
      this.replica = new SpecReplica(id, specs, new ChangeLog(db), conflicts, new SyncState(db));
    }

    @Override
    public void close() {
      db.close();
    }
  }

  @BeforeEach
  void setUp() {
    main = new Box("main");
    node = new Box("node");
    other = new Box("other");
  }

  @AfterEach
  void tearDown() {
    other.close();
    node.close();
    main.close();
  }

  private SpecStore.SpecRow spec(String id, String title, String status) {
    return new SpecStore.SpecRow(
        id,
        "proj",
        title,
        SpecStatus.fromWire(status),
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

  private void sync(Box box) {
    engine.reconcile(box.replica, main.replica);
  }

  private SyncConflicts.Conflict raiseTitleConflict() {
    node.specs.create(spec("auth", "Auth", "pending"));
    sync(node);
    sync(other);

    other.specs.update(spec("auth", "Title from other", "pending"));
    sync(other);

    node.specs.update(spec("auth", "Title from node", "pending"));
    sync(node);

    return node.conflicts.pendingFor("spec", "auth").orElseThrow();
  }

  private Map<String, Object> snapshot(String json) {
    return json == null || json.isBlank() ? null : YamlUtil.parseMap(json);
  }

  @Test
  void keepMinePushesTheLocalValueAndConvergesWithoutReConflict() {
    var conflict = raiseTitleConflict();

    var mine = snapshot(conflict.localSnapshot());
    var theirs = snapshot(conflict.remoteSnapshot());
    var rev = node.specs.resolveConflict("auth", mine, theirs);
    node.conflicts.resolve(conflict.id(), rev);

    sync(node);
    sync(other);

    assertEquals("Title from node", main.specs.findById("auth").orElseThrow().title());
    assertEquals("Title from node", other.specs.findById("auth").orElseThrow().title());
    assertEquals("Title from node", node.specs.findById("auth").orElseThrow().title());
    assertTrue(node.conflicts.pending().isEmpty());
  }

  @Test
  void takeTheirsAdoptsMainAndRetainsMineInHistory() {
    var conflict = raiseTitleConflict();

    var theirs = snapshot(conflict.remoteSnapshot());
    var rev = node.specs.resolveConflict("auth", theirs, theirs);
    node.conflicts.resolve(conflict.id(), rev);

    assertEquals("Title from other", node.specs.findById("auth").orElseThrow().title());

    var second = engine.reconcile(node.replica, main.replica);
    assertEquals(0, second.conflicts());
    assertEquals("Title from other", main.specs.findById("auth").orElseThrow().title());
    assertTrue(
        node.specs.history("auth").stream().anyMatch(e -> e.snapshot().contains("Title from node")),
        "the local version is retained in the change log");
  }

  @Test
  void mergePushesAThirdValueAndConverges() {
    var conflict = raiseTitleConflict();

    var theirs = snapshot(conflict.remoteSnapshot());
    var merged = new LinkedHashMap<>(snapshot(conflict.localSnapshot()));
    merged.put("title", "Merged title");
    var rev = node.specs.resolveConflict("auth", merged, theirs);
    node.conflicts.resolve(conflict.id(), rev);

    sync(node);
    sync(other);

    assertEquals("Merged title", main.specs.findById("auth").orElseThrow().title());
    assertEquals("Merged title", other.specs.findById("auth").orElseThrow().title());
    assertTrue(node.conflicts.pending().isEmpty());
  }

  @Test
  void resolvingDeleteVersusEditByTakingTheRemoteEditReCreatesLocally() {
    node.specs.create(spec("auth", "Auth", "pending"));
    sync(node);
    sync(other);

    node.specs.delete("auth");
    other.specs.update(spec("auth", "Edited by other", "pending"));
    sync(other);
    sync(node);

    var conflict = node.conflicts.pendingFor("spec", "auth").orElseThrow();
    assertEquals(List.of("<deleted>"), conflict.fields());

    var theirs = snapshot(conflict.remoteSnapshot());
    var rev = node.specs.resolveConflict("auth", theirs, theirs);
    node.conflicts.resolve(conflict.id(), rev);

    assertEquals("Edited by other", node.specs.findById("auth").orElseThrow().title());
    var second = engine.reconcile(node.replica, main.replica);
    assertEquals(0, second.conflicts());
  }

  @Test
  void resolvingDeleteVersusEditByKeepingTheLocalDeleteRemovesItEverywhere() {
    node.specs.create(spec("auth", "Auth", "pending"));
    sync(node);
    sync(other);

    node.specs.delete("auth");
    other.specs.update(spec("auth", "Edited by other", "pending"));
    sync(other);
    sync(node);

    var conflict = node.conflicts.pendingFor("spec", "auth").orElseThrow();
    var theirs = snapshot(conflict.remoteSnapshot());
    var rev = node.specs.resolveConflict("auth", null, theirs);
    node.conflicts.resolve(conflict.id(), rev);

    assertTrue(node.specs.findById("auth").isEmpty());
    sync(node);
    sync(other);
    assertTrue(main.specs.findById("auth").isEmpty());
    assertTrue(other.specs.findById("auth").isEmpty());
  }

  @Test
  void resolvingEditVersusRemoteDeleteByKeepingMineReCreatesOnMain() {
    other.specs.create(spec("auth", "Auth", "pending"));
    sync(other);
    sync(node);

    other.specs.delete("auth");
    node.specs.update(spec("auth", "Kept by node", "pending"));
    sync(other);
    sync(node);

    var conflict = node.conflicts.pendingFor("spec", "auth").orElseThrow();
    assertEquals(List.of("<deleted>"), conflict.fields());

    var mine = snapshot(conflict.localSnapshot());
    var rev = node.specs.resolveConflict("auth", mine, null);
    node.conflicts.resolve(conflict.id(), rev);

    assertEquals("Kept by node", node.specs.findById("auth").orElseThrow().title());
    sync(node);
    sync(other);
    assertEquals("Kept by node", main.specs.findById("auth").orElseThrow().title());
    assertEquals("Kept by node", other.specs.findById("auth").orElseThrow().title());
  }
}
