/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.commands;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.singlr.sail.config.SyncConfig;
import ai.singlr.sail.engine.ContainerManager;
import ai.singlr.sail.engine.ContainerSpecImporter;
import ai.singlr.sail.engine.ScriptedShellExecutor;
import ai.singlr.sail.store.DataMigration;
import ai.singlr.sail.store.SchemaManager;
import ai.singlr.sail.store.SpecStore;
import ai.singlr.sail.store.Sqlite;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrateCommandTest {

  @TempDir Path tempDir;
  private Sqlite db;

  @BeforeEach
  void setUp() {
    db = Sqlite.open(tempDir.resolve("test.db"));
  }

  @AfterEach
  void tearDown() {
    if (db != null) db.close();
  }

  @Test
  void schemaIsFullyMigratedBeforeSpecImportRuns() {
    var versionWhenImportRan = new AtomicInteger(-1);

    MigrateCommand.applyMigrations(
        db,
        "test.db",
        () -> {
          versionWhenImportRan.set(new SchemaManager(db).currentVersion());
          return ContainerSpecImporter.Report.empty();
        },
        DataMigration.Prompter.NON_INTERACTIVE,
        false,
        true);

    var finalVersion = new SchemaManager(db).currentVersion();
    assertTrue(finalVersion > 0, "schema should have migrated from empty");
    assertEquals(
        finalVersion,
        versionWhenImportRan.get(),
        "schema must be fully migrated before the spec import queries SpecStore");
  }

  private ContainerSpecImporter importerOverEmptyProjects() {
    var shell = new ScriptedShellExecutor();
    return new ContainerSpecImporter(
        shell, new ContainerManager(shell), new SpecStore(db), tempDir.resolve("no-projects"));
  }

  @Test
  void specImportIsGatedAwayWhenThisBoxIsAMainOrNode() throws Exception {
    var marker = tempDir.resolve("spec-import.done");

    var asMain =
        MigrateCommand.specImportStep(
            importerOverEmptyProjects(), marker, new SyncConfig(SyncConfig.ROLE_MAIN, null));
    var asNode =
        MigrateCommand.specImportStep(
            importerOverEmptyProjects(),
            marker,
            new SyncConfig(SyncConfig.ROLE_NODE, "sail@maindevbox"));

    assertEquals(0, asMain.get().imported());
    assertEquals(0, asNode.get().imported());
    assertFalse(Files.exists(marker), "a main/node never scans, so it never marks");
  }

  @Test
  void specImportRunsOnceOnAStandaloneBoxThenStops() throws Exception {
    var marker = tempDir.resolve("spec-import.done");

    var first =
        MigrateCommand.specImportStep(importerOverEmptyProjects(), marker, SyncConfig.unset());
    first.get();
    assertTrue(Files.exists(marker), "standalone backfill records that it ran");

    var second =
        MigrateCommand.specImportStep(importerOverEmptyProjects(), marker, SyncConfig.unset());
    assertEquals(0, second.get().imported());
    assertTrue(Files.exists(marker));
  }

  @Test
  void specImportSeesCurrentSchemaColumns() {
    MigrateCommand.applyMigrations(
        db,
        "test.db",
        () -> {
          assertDoesNotThrow(() -> new ai.singlr.sail.store.SpecStore(db).findById("none"));
          return ContainerSpecImporter.Report.empty();
        },
        DataMigration.Prompter.NON_INTERACTIVE,
        false,
        true);
  }
}
