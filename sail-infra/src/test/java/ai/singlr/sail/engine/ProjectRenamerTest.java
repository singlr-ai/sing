/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.singlr.sail.config.SpecStatus;
import ai.singlr.sail.store.FileStore;
import ai.singlr.sail.store.ProjectStore;
import ai.singlr.sail.store.SchemaManager;
import ai.singlr.sail.store.SpecStore;
import ai.singlr.sail.store.Sqlite;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectRenamerTest {

  private static final String RUNNING = "[{\"name\": \"old\", \"status\": \"Running\"}]";
  private static final String DEFINITION =
      "name: old\nimage: ubuntu/24.04\n"
          + "resources:\n  cpu: 2\n  memory: 4GB\n  disk: 20GB\n"
          + "agent:\n  type: claude-code\nssh:\n  user: dev\n";

  @TempDir Path tempDir;
  private Sqlite db;
  private Path projectsDir;

  @BeforeEach
  void setUp() throws Exception {
    db = Sqlite.open(tempDir.resolve("test.db"));
    new SchemaManager(db).migrate();
    new ProjectStore(db).upsert("old", DEFINITION, "uday");
    new SpecStore(db).create(spec("s1", "old"));
    new FileStore(db).put("old", "start-dev.sh", "ZWNobyBoaQ==");
    projectsDir = tempDir.resolve("projects");
    Files.createDirectories(projectsDir.resolve("old"));
    Files.writeString(projectsDir.resolve("old").resolve("sail.yaml"), DEFINITION);
  }

  @AfterEach
  void tearDown() {
    if (db != null) db.close();
  }

  private static SpecStore.SpecRow spec(String id, String project) {
    return new SpecStore.SpecRow(
        id,
        project,
        "Spec",
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

  private static ScriptedShellExecutor runningShell() {
    return new ScriptedShellExecutor(new ShellExec.Result(0, "", ""))
        .onOk("incus list ^old$", RUNNING);
  }

  private ProjectRenamer renamer(ScriptedShellExecutor shell) {
    return new ProjectRenamer(db, shell, projectsDir);
  }

  @Test
  void renamesContainerCatalogSpecsFilesAndState() throws Exception {
    var shell = runningShell();

    var result = renamer(shell).rename("old", "renamed");

    assertTrue(result.containerRenamed());
    assertTrue(new ProjectStore(db).findByName("renamed").isPresent());
    assertTrue(new ProjectStore(db).findByName("old").isEmpty());
    assertEquals(1, new SpecStore(db).projectSpecs("renamed").size());
    assertTrue(new SpecStore(db).projectSpecs("old").isEmpty());
    assertTrue(new FileStore(db).find("renamed", "start-dev.sh").isPresent());
    assertTrue(Files.exists(projectsDir.resolve("renamed").resolve("sail.yaml")));
    assertFalse(Files.exists(projectsDir.resolve("old")));
    var cmds = shell.invocations();
    assertTrue(cmds.stream().anyMatch(c -> c.equals("incus rename old renamed")));
    assertTrue(
        cmds.stream().anyMatch(c -> c.contains("/etc/hostname")),
        "the guest hostname is updated so the spec CLI files under the new name");
  }

  @Test
  void renamesCatalogOnlyWhenThereIsNoContainer() throws Exception {
    var shell = new ScriptedShellExecutor(new ShellExec.Result(0, "", ""));

    var result = renamer(shell).rename("old", "renamed");

    assertFalse(result.containerRenamed());
    assertTrue(new ProjectStore(db).findByName("renamed").isPresent());
    assertTrue(Files.exists(projectsDir.resolve("renamed")));
    assertFalse(
        shell.invocations().stream().anyMatch(c -> c.contains("incus rename")),
        "no container to rename");
  }

  @Test
  void collectsAWarningWhenAFinishStepFails() throws Exception {
    var shell = runningShell().onFail("/etc/hostname", "read-only file system");

    var result = renamer(shell).rename("old", "renamed");

    assertTrue(
        new ProjectStore(db).findByName("renamed").isPresent(), "the rename still committed");
    assertTrue(
        result.warnings().stream().anyMatch(w -> w.contains("hostname")),
        "a best-effort failure is reported, not fatal");
  }

  @Test
  void rollsBackWhenTheContainerRenameFails() {
    var shell = runningShell().onFail("incus rename", "instance is busy");

    assertThrows(Exception.class, () -> renamer(shell).rename("old", "renamed"));

    assertTrue(new ProjectStore(db).findByName("old").isPresent(), "the catalog is untouched");
    assertTrue(new ProjectStore(db).findByName("renamed").isEmpty());
    assertTrue(
        shell.invocations().stream().anyMatch(c -> c.equals("incus start old")),
        "the stop is compensated by restarting the original container");
  }

  @Test
  void rejectsRenamingToTheSameName() {
    assertThrows(
        IllegalArgumentException.class, () -> renamer(runningShell()).rename("old", "old"));
  }

  @Test
  void rejectsAnUnknownProject() {
    assertThrows(
        IllegalStateException.class, () -> renamer(runningShell()).rename("ghost", "renamed"));
  }

  @Test
  void rejectsATargetNameAlreadyInTheCatalog() throws Exception {
    new ProjectStore(db).upsert("taken", "name: taken\n", "uday");

    assertThrows(IllegalStateException.class, () -> renamer(runningShell()).rename("old", "taken"));
  }
}
