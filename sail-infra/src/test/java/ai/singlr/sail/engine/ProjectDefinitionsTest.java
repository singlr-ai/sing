/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.singlr.sail.store.ProjectStore;
import ai.singlr.sail.store.SchemaManager;
import ai.singlr.sail.store.Sqlite;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectDefinitionsTest {

  @TempDir Path dir;
  private Sqlite db;
  private ProjectStore store;

  @BeforeEach
  void setUp() {
    db = Sqlite.open(dir.resolve("test.db"));
    new SchemaManager(db).migrate();
    store = new ProjectStore(db);
  }

  @AfterEach
  void tearDown() {
    db.close();
  }

  @Test
  void resolvePrefersTheCatalogOverTheCanonicalFile() throws Exception {
    store.upsert("acme", "from-db", "uday");
    var canonical = dir.resolve("acme.yaml");
    Files.writeString(canonical, "from-file");

    assertEquals(
        "from-db", ProjectDefinitions.resolve(store, "acme", null, canonical).orElseThrow());
  }

  @Test
  void resolveFallsBackToTheCanonicalFileWhenNotCatalogued() throws Exception {
    var canonical = dir.resolve("acme.yaml");
    Files.writeString(canonical, "only-on-disk");

    assertEquals(
        "only-on-disk", ProjectDefinitions.resolve(store, "acme", null, canonical).orElseThrow());
  }

  @Test
  void resolveLetsAnExplicitFileOverrideTheCatalog() throws Exception {
    store.upsert("acme", "from-db", "uday");
    var explicit = dir.resolve("override.yaml");
    Files.writeString(explicit, "from-explicit");
    var canonical = dir.resolve("acme.yaml");

    assertEquals(
        "from-explicit",
        ProjectDefinitions.resolve(store, "acme", explicit, canonical).orElseThrow());
  }

  @Test
  void resolveIsEmptyWhenNoSourceHasTheProject() {
    assertTrue(
        ProjectDefinitions.resolve(store, "ghost", null, dir.resolve("absent.yaml")).isEmpty());
  }

  @Test
  void explicitFileTreatsTheBareDefaultAsDatabaseFirst() {
    assertEquals(null, ProjectDefinitions.explicitFile("sail.yaml"));
    assertEquals(null, ProjectDefinitions.explicitFile(null));
    assertEquals(Path.of("custom.yaml"), ProjectDefinitions.explicitFile("custom.yaml"));
  }
}
