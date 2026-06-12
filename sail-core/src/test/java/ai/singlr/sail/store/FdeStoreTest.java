/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.store;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FdeStoreTest {

  @TempDir Path tempDir;
  private Sqlite db;
  private FdeStore store;

  @BeforeEach
  void setUp() {
    db = Sqlite.open(tempDir.resolve("test.db"));
    new SchemaManager(db).migrate();
    store = new FdeStore(db);
  }

  @AfterEach
  void tearDown() {
    if (db != null) db.close();
  }

  @Test
  void removeDeletesTheFdeItsTokensAndCascadesCredentials() {
    var fde = store.add("ghost", null, null, "member");
    new TokenStore(db).create("ghost-token", "member", fde.id());
    new AuthSessionStore(db).create(fde.id(), java.time.Duration.ofMinutes(5));
    new FdeSshKeyStore(db)
        .add(
            fde.id(),
            ai.singlr.sail.ssh.SshPublicKey.parse(
                ai.singlr.sail.ssh.TestSshKeys.ed25519("seed", "g@m")));

    store.remove(fde.id());

    assertTrue(store.byHandle("ghost").isEmpty());
    assertEquals(0L, count("api_tokens", fde.id()));
    assertEquals(0L, count("sessions", fde.id()));
    assertEquals(0L, count("fde_ssh_keys", fde.id()));
  }

  @Test
  void removeLeavesOtherFdesCredentialsAlone() {
    var ghost = store.add("ghost", null, null, "member");
    var keeper = store.add("keeper", null, null, "member");
    new TokenStore(db).create("keeper-token", "member", keeper.id());

    store.remove(ghost.id());

    assertTrue(store.byHandle("keeper").isPresent());
    assertEquals(1L, count("api_tokens", keeper.id()));
  }

  @Test
  void activeAdminCountTracksRoleAndStatus() {
    assertEquals(0L, store.activeAdminCount());
    var admin = store.add("ada", null, null, "admin");
    store.add("bob", null, null, "member");
    assertEquals(1L, store.activeAdminCount());
    db.execute("UPDATE fdes SET status = 'disabled' WHERE id = ?", admin.id());
    assertEquals(0L, store.activeAdminCount());
  }

  private long count(String table, String fdeId) {
    return db.queryOne(
            "SELECT COUNT(*) FROM " + table + " WHERE fde_id = ?", row -> row.integer(0), fdeId)
        .orElse(-1L);
  }

  @Test
  void addAssignsGeneratedIdAndActiveStatus() {
    var fde = store.add("uday", "Uday Chandra", "uday@singlr.ai");
    assertTrue(fde.id().startsWith("fde_"));
    assertEquals("uday", fde.handle());
    assertEquals("Uday Chandra", fde.displayName());
    assertEquals("uday@singlr.ai", fde.email());
    assertEquals("member", fde.role());
    assertEquals("active", fde.status());
  }

  @Test
  void addStoresAndRoundTripsExplicitRole() {
    store.add("admin-user", null, null, "admin");
    assertEquals("admin", store.byHandle("admin-user").orElseThrow().role());
  }

  @Test
  void addRejectsUnknownRole() {
    assertThrows(IllegalArgumentException.class, () -> store.add("bad", null, null, "superuser"));
  }

  @Test
  void byHandleAndByIdResolveTheSameRecord() {
    var added = store.add("nova", null, null);
    assertEquals(added.id(), store.byHandle("nova").orElseThrow().id());
    assertEquals("nova", store.byId(added.id()).orElseThrow().handle());
  }

  @Test
  void lookupsReturnEmptyWhenAbsent() {
    assertTrue(store.byHandle("ghost").isEmpty());
    assertTrue(store.byId("fde_missing").isEmpty());
  }

  @Test
  void listIsOrderedByHandle() {
    store.add("zara", null, null);
    store.add("amir", null, null);
    var handles = store.list().stream().map(FdeStore.Fde::handle).toList();
    assertEquals(java.util.List.of("amir", "zara"), handles);
  }

  @Test
  void duplicateHandleIsRejected() {
    store.add("dup", null, null);
    assertThrows(SqliteException.class, () -> store.add("dup", null, null));
  }
}
