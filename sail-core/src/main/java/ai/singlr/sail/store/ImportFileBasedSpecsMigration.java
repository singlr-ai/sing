/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.store;

import ai.singlr.sail.config.ProjectRegistry;
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
 * <p>The scan root defaults to {@code ~/workspace} (the canonical FDE layout) but can be overridden
 * via the {@code sail.migrate.workspace} system property, which {@code sail migrate --workspace
 * <dir>} sets before invoking the data-migrator.
 */
public final class ImportFileBasedSpecsMigration implements DataMigration {

  public static final String NAME = "import-workspace-specs-2026-05";
  public static final String WORKSPACE_PROPERTY = "sail.migrate.workspace";

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Report apply(Sqlite db, ProjectRegistry projects, Prompter prompter) {
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
    return new Report(imported, 0, skipped, List.copyOf(notes));
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
