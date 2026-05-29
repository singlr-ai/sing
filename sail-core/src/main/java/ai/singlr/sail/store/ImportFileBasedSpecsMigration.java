/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Imports every pre-control-plane file-based spec into the SQLite database. Walks {@code
 * <workspace>/<project>/specs/} for each project workspace and registers every {@code spec.yaml}
 * found, assigning {@code project = <workspace-dir-name>} so the import comes in already bucketed.
 * Existing rows in the DB are not overwritten — {@link SpecMigrator} skips by id.
 *
 * <p>NOT tracked in {@code data_migrations}: this is a discovery operation, not a one-shot schema
 * fix-up. It runs every time {@code sail migrate} is invoked and is intrinsically idempotent
 * (skip-by-id). The {@code --workspace} flag overrides the default {@code ~/workspace} scan root
 * via the {@link #WORKSPACE_PROPERTY} system property.
 *
 * <p>Deliberately not invoked from {@link ai.singlr.sail.store.MigrationRunner} or {@code
 * ServerStartCommand} — the daemon has no business knowing about per-operator workspace paths. Only
 * {@code sail migrate} drives it.
 */
public final class ImportFileBasedSpecsMigration {

  public static final String WORKSPACE_PROPERTY = "sail.migrate.workspace";

  /**
   * Per-run summary surfaced to the {@code sail migrate} CLI for printing.
   *
   * @param imported newly-inserted specs
   * @param skipped specs already in the DB (skip-by-id)
   * @param notes one line per project workspace the scan touched
   */
  public record Report(int imported, int skipped, List<String> notes) {
    public static Report empty() {
      return new Report(0, 0, List.of());
    }
  }

  public Report apply(Sqlite db) {
    var workspace = resolveWorkspace();
    if (workspace == null || !Files.isDirectory(workspace)) {
      return Report.empty();
    }
    var specStore = new SpecStore(db);
    var migrator = new SpecMigrator(specStore);
    var imported = 0;
    var skipped = 0;
    var notes = new ArrayList<String>();
    try (var stream = Files.list(workspace)) {
      var projectDirs = stream.filter(Files::isDirectory).sorted().toList();
      for (var projectDir : projectDirs) {
        var specsDir = projectDir.resolve("specs");
        if (!Files.isDirectory(specsDir)) {
          continue;
        }
        var projectName = projectDir.getFileName().toString();
        var result = migrator.importFromDirectory(specsDir, projectName);
        imported += result.imported();
        skipped += result.skipped();
        if (result.imported() > 0 || result.skipped() > 0) {
          notes.add(
              "  • "
                  + projectName
                  + ": imported "
                  + result.imported()
                  + ", skipped (already present) "
                  + result.skipped()
                  + " from "
                  + specsDir);
        }
        for (var error : result.errors()) {
          notes.add("  • " + projectName + " ERROR: " + error);
        }
      }
    } catch (IOException e) {
      notes.add("  • Failed to list workspace " + workspace + ": " + e.getMessage());
    }
    return new Report(imported, skipped, List.copyOf(notes));
  }

  private static Path resolveWorkspace() {
    var override = System.getProperty(WORKSPACE_PROPERTY);
    if (override != null && !override.isBlank()) {
      return Path.of(override);
    }
    var home = System.getProperty("user.home");
    if (home == null || home.isBlank()) {
      return null;
    }
    return Path.of(home, "workspace");
  }
}
