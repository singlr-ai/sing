/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.singlr.sail.config.SailYaml;
import ai.singlr.sail.config.YamlUtil;
import ai.singlr.sail.store.ProjectStore;
import ai.singlr.sail.store.SchemaManager;
import ai.singlr.sail.store.Sqlite;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemoSeederTest {

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
  void seedsTheDemoIntoAnEmptyCatalog() {
    assertTrue(DemoSeeder.seedIfAbsent(db));

    var row = store.findByName(DemoProject.NAME).orElseThrow();
    assertEquals(DemoProject.DEFINITION, row.definition());
  }

  @Test
  void isIdempotentAndNeverClobbersAnExistingDemo() {
    store.upsert(DemoProject.NAME, "name: demo\ndescription: mine\n", "me");

    assertFalse(DemoSeeder.seedIfAbsent(db), "must not re-seed when a demo already exists");
    assertEquals(
        "name: demo\ndescription: mine\n",
        store.findByName(DemoProject.NAME).orElseThrow().definition(),
        "a customised demo is left untouched");
  }

  @Test
  void seededDemoDefinitionParsesAsAValidProject() {
    DemoSeeder.seedIfAbsent(db);
    var definition = store.findByName(DemoProject.NAME).orElseThrow().definition();

    var config = SailYaml.fromMap(YamlUtil.parseMap(definition));
    assertEquals("demo", config.name());
  }

  @Test
  void bundlesTheOutlineSetupScript() {
    assertTrue(DemoProject.files().containsKey("outline/setup.sh"));
    assertTrue(DemoProject.files().get("outline/setup.sh").contains("Outline Dev Environment"));
  }
}
