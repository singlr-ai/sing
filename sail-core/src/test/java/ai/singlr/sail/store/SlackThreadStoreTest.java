/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SlackThreadStoreTest {

  @TempDir Path tempDir;
  private Sqlite db;
  private SlackThreadStore store;

  @BeforeEach
  void setUp() {
    db = Sqlite.open(tempDir.resolve("test.db"));
    new SchemaManager(db).migrate();
    store = new SlackThreadStore(db);
  }

  @AfterEach
  void tearDown() {
    if (db != null) db.close();
  }

  @Test
  void findReturnsEmptyWhenNoThreadRecorded() {
    assertTrue(store.find("light", "auth").isEmpty());
  }

  @Test
  void saveThenFindReturnsThread() {
    store.save("light", "auth", "C123", "1700000000.000100");

    var thread = store.find("light", "auth").orElseThrow();
    assertEquals("C123", thread.channel());
    assertEquals("1700000000.000100", thread.threadTs());
  }

  @Test
  void saveReplacesExistingThreadOnRedispatch() {
    store.save("light", "auth", "C123", "1700000000.000100");
    store.save("light", "auth", "C123", "1700000099.000200");

    var thread = store.find("light", "auth").orElseThrow();
    assertEquals("1700000099.000200", thread.threadTs());
  }

  @Test
  void threadsAreScopedByProjectAndSpec() {
    store.save("light", "auth", "C1", "1.1");
    store.save("light", "billing", "C1", "2.2");
    store.save("grid", "auth", "C2", "3.3");

    assertEquals("1.1", store.find("light", "auth").orElseThrow().threadTs());
    assertEquals("2.2", store.find("light", "billing").orElseThrow().threadTs());
    assertEquals("3.3", store.find("grid", "auth").orElseThrow().threadTs());
  }
}
