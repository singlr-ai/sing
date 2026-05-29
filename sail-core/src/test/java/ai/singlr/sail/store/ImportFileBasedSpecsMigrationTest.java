/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.store;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImportFileBasedSpecsMigrationTest {

  @TempDir Path tempDir;
  private Sqlite db;

  @BeforeEach
  void setUp() {
    db = Sqlite.open(tempDir.resolve("test.db"));
    new SchemaManager(db).migrate();
  }

  @AfterEach
  void tearDown() {
    if (db != null) db.close();
    System.clearProperty(ImportFileBasedSpecsMigration.WORKSPACE_PROPERTY);
  }

  @Test
  void importsSpecFromProjectWorkspace() throws Exception {
    var workspace = tempDir.resolve("workspace");
    var specYaml =
        workspace.resolve("manatee").resolve("specs").resolve("oauth-flow").resolve("spec.yaml");
    Files.createDirectories(specYaml.getParent());
    Files.writeString(specYaml, "id: oauth-flow\ntitle: OAuth flow\nstatus: pending\n");
    System.setProperty(ImportFileBasedSpecsMigration.WORKSPACE_PROPERTY, workspace.toString());

    var report = new ImportFileBasedSpecsMigration().apply(db);

    assertEquals(1, report.imported());
    var spec = new SpecStore(db).findById("oauth-flow").orElseThrow();
    assertEquals("manatee", spec.project());
    assertEquals("OAuth flow", spec.title());
  }

  @Test
  void assignsProjectFromWorkspaceDirName() throws Exception {
    var workspace = tempDir.resolve("workspace");
    var bothSpecs =
        new String[] {
          workspace
              .resolve("manatee")
              .resolve("specs")
              .resolve("a")
              .resolve("spec.yaml")
              .toString(),
          workspace.resolve("beta").resolve("specs").resolve("b").resolve("spec.yaml").toString()
        };
    for (var s : bothSpecs) {
      var p = Path.of(s);
      Files.createDirectories(p.getParent());
      var id = p.getParent().getFileName().toString();
      Files.writeString(p, "id: " + id + "\ntitle: t\nstatus: pending\n");
    }
    System.setProperty(ImportFileBasedSpecsMigration.WORKSPACE_PROPERTY, workspace.toString());

    new ImportFileBasedSpecsMigration().apply(db);

    var store = new SpecStore(db);
    assertEquals("manatee", store.findById("a").orElseThrow().project());
    assertEquals("beta", store.findById("b").orElseThrow().project());
  }

  @Test
  void skipsExistingSpecsInDb() throws Exception {
    var workspace = tempDir.resolve("workspace");
    var specYaml =
        workspace.resolve("manatee").resolve("specs").resolve("dup").resolve("spec.yaml");
    Files.createDirectories(specYaml.getParent());
    Files.writeString(specYaml, "id: dup\ntitle: from disk\nstatus: pending\n");
    System.setProperty(ImportFileBasedSpecsMigration.WORKSPACE_PROPERTY, workspace.toString());

    new SpecStore(db)
        .create(
            new SpecStore.SpecRow(
                "dup",
                "preexisting",
                "from db",
                "draft",
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                "",
                "",
                java.util.List.of(),
                java.util.List.of()));

    var report = new ImportFileBasedSpecsMigration().apply(db);

    assertEquals(0, report.imported());
    assertEquals(1, report.skipped());
    var kept = new SpecStore(db).findById("dup").orElseThrow();
    assertEquals("preexisting", kept.project());
    assertEquals("from db", kept.title());
  }

  @Test
  void emptyWorkspaceIsNoop() {
    System.setProperty(
        ImportFileBasedSpecsMigration.WORKSPACE_PROPERTY,
        tempDir.resolve("nonexistent").toString());

    var report = new ImportFileBasedSpecsMigration().apply(db);

    assertEquals(0, report.imported());
    assertEquals(0, report.skipped());
  }

  @Test
  void projectsWithoutSpecsDirAreSkipped() throws Exception {
    var workspace = tempDir.resolve("workspace");
    Files.createDirectories(workspace.resolve("just-a-repo"));
    System.setProperty(ImportFileBasedSpecsMigration.WORKSPACE_PROPERTY, workspace.toString());

    var report = new ImportFileBasedSpecsMigration().apply(db);

    assertEquals(0, report.imported());
    assertTrue(report.notes().isEmpty());
  }
}
