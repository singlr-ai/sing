/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.singlr.sail.store.SchemaManager;
import ai.singlr.sail.store.SpecStore;
import ai.singlr.sail.store.Sqlite;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContainerSpecImporterTest {

  private static final String SAIL_YAML =
      """
      name: manatee
      agent:
        type: claude-code
        specs_dir: specs
      """;

  private static final String OAUTH_METADATA =
      """
      id: oauth-flow
      title: OAuth Flow
      status: pending
      """;

  @TempDir Path projectsDir;
  private Sqlite db;
  private SpecStore store;

  @BeforeEach
  void setUp() {
    db = Sqlite.open(projectsDir.resolve("test.db"));
    new SchemaManager(db).migrate();
    store = new SpecStore(db);
  }

  @AfterEach
  void tearDown() {
    if (db != null) db.close();
  }

  private void writeDescriptor(String project) throws Exception {
    var dir = projectsDir.resolve(project);
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("sail.yaml"), SAIL_YAML);
  }

  private static ScriptedShellExecutor runningContainerWithOauthSpec(String project) {
    var specsDir = "/home/dev/workspace/specs";
    return new ScriptedShellExecutor()
        .onOk(
            "incus list ^" + project + "$",
            """
            [{"name":"%s","status":"Running"}]
            """
                .formatted(project))
        .onOk(
            "find " + specsDir + " -mindepth 2 -maxdepth 2 -name spec.yaml -print",
            specsDir + "/oauth-flow/spec.yaml\n")
        .onOk("cat " + specsDir + "/oauth-flow/spec.yaml", OAUTH_METADATA)
        .onOk("cat " + specsDir + "/oauth-flow/spec.md", "# OAuth\nBody text.")
        .onFail("cat " + specsDir + "/oauth-flow/plan.md", "No such file or directory");
  }

  @Test
  void importsSpecsFromRunningContainerBucketedByProject() throws Exception {
    writeDescriptor("manatee");
    var shell = runningContainerWithOauthSpec("manatee");
    var importer =
        new ContainerSpecImporter(shell, new ContainerManager(shell), store, projectsDir);

    var report = importer.importAll();

    assertEquals(1, report.imported());
    assertEquals(0, report.skipped());
    var stored = store.findById("oauth-flow");
    assertTrue(stored.isPresent());
    assertEquals("manatee", stored.get().project());
  }

  @Test
  void secondRunSkipsAlreadyImportedSpecs() throws Exception {
    writeDescriptor("manatee");
    var shell = runningContainerWithOauthSpec("manatee");
    new ContainerSpecImporter(shell, new ContainerManager(shell), store, projectsDir).importAll();

    var rerun = runningContainerWithOauthSpec("manatee");
    var report =
        new ContainerSpecImporter(rerun, new ContainerManager(rerun), store, projectsDir)
            .importAll();

    assertEquals(0, report.imported());
    assertEquals(1, report.skipped());
  }

  @Test
  void skipsStoppedContainerWithoutReadingSpecs() throws Exception {
    writeDescriptor("manatee");
    var shell =
        new ScriptedShellExecutor()
            .onOk(
                "incus list ^manatee$",
                """
                [{"name":"manatee","status":"Stopped"}]
                """);
    var importer =
        new ContainerSpecImporter(shell, new ContainerManager(shell), store, projectsDir);

    var report = importer.importAll();

    assertEquals(0, report.imported());
    assertTrue(report.notes().getFirst().contains("container stopped"));
    assertTrue(shell.invocations().stream().noneMatch(cmd -> cmd.contains("find")));
  }

  @Test
  void emptyWhenNoProjectsDirectory() {
    var shell = new ScriptedShellExecutor();
    var importer =
        new ContainerSpecImporter(
            shell, new ContainerManager(shell), store, projectsDir.resolve("missing"));

    var report = importer.importAll();

    assertEquals(0, report.imported());
    assertTrue(report.notes().isEmpty());
  }
}
