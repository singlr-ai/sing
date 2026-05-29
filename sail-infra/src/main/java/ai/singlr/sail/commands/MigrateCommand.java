/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.commands;

import ai.singlr.sail.engine.SailPaths;
import ai.singlr.sail.store.DataMigration;
import ai.singlr.sail.store.DataMigrator;
import ai.singlr.sail.store.ImportFileBasedSpecsMigration;
import ai.singlr.sail.store.MigrationRunner;
import ai.singlr.sail.store.RebucketSpecsMigration;
import ai.singlr.sail.store.Sqlite;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Single command that runs every pending schema + data migration on the control-plane database.
 * Idempotent: schema migrations are tracked by version, data migrations by name, so re-runs do
 * nothing. {@code sail upgrade} calls this at the end so future upgrades need no manual step.
 */
@Command(
    name = "migrate",
    description = "Apply all pending schema and data migrations.",
    mixinStandardHelpOptions = true)
public final class MigrateCommand implements Runnable {

  /**
   * Every one-shot data migration tracked in {@code data_migrations}. Add new ones at the bottom.
   * File-based spec discovery (see {@link ImportFileBasedSpecsMigration}) deliberately runs outside
   * this registry — it's a repeatable scan, not a one-shot fix-up.
   */
  public static final List<ai.singlr.sail.store.DataMigration> REGISTRY =
      List.of(new RebucketSpecsMigration());

  @Option(
      names = "--non-interactive",
      description = "Skip prompts; leave ambiguous rows for manual follow-up.")
  private boolean nonInteractive;

  @Option(
      names = "--workspace",
      description =
          "Override the workspace root scanned for file-based specs. Defaults to ~/workspace.")
  private java.nio.file.Path workspace;

  @Option(names = "--json", description = "Output JSON instead of human-readable text.")
  private boolean json;

  @Spec private CommandSpec spec;

  @Override
  public void run() {
    CliCommand.run(spec, this::execute);
  }

  private void execute() throws Exception {
    if (workspace != null) {
      System.setProperty(ImportFileBasedSpecsMigration.WORKSPACE_PROPERTY, workspace.toString());
    }
    try {
      runMigrations(nonInteractive, json);
    } finally {
      if (workspace != null) {
        System.clearProperty(ImportFileBasedSpecsMigration.WORKSPACE_PROPERTY);
      }
    }
  }

  /**
   * Reusable entry point: opens the DB, applies schema + data migrations, returns the data-runs for
   * the caller (UpgradeCommand wires this in at the end of the upgrade flow).
   */
  public static List<DataMigrator.Run> runMigrations(boolean nonInteractive, boolean jsonOutput) {
    var dbPath = SailPaths.sailDir().resolve("sail.db");
    try {
      Files.createDirectories(dbPath.getParent());
    } catch (Exception e) {
      throw new IllegalStateException("Could not prepare " + dbPath.getParent(), e);
    }
    try (var db = Sqlite.open(dbPath)) {
      var prompter = nonInteractive ? DataMigration.Prompter.NON_INTERACTIVE : ttyPrompter();
      var importReport = new ImportFileBasedSpecsMigration().apply(db);
      var result = MigrationRunner.applyAll(db, REGISTRY, prompter);
      if (!jsonOutput) {
        printSummary(
            dbPath.toString(),
            result.schemaBefore(),
            result.schemaAfter(),
            importReport,
            result.dataRuns());
      }
      return result.dataRuns();
    }
  }

  private static void printSummary(
      String dbPath,
      int beforeSchema,
      int afterSchema,
      ImportFileBasedSpecsMigration.Report importReport,
      List<DataMigrator.Run> runs) {
    System.out.println(Ansi.AUTO.string("  @|green ✓|@ Database: " + dbPath));
    if (afterSchema > beforeSchema) {
      System.out.println(
          Ansi.AUTO.string(
              "    @|faint Schema migrated: " + beforeSchema + " → " + afterSchema + "|@"));
    } else {
      System.out.println(
          Ansi.AUTO.string("    @|faint Schema up to date (version " + afterSchema + ")|@"));
    }
    if (importReport.imported() > 0
        || importReport.skipped() > 0
        || !importReport.notes().isEmpty()) {
      System.out.println(
          Ansi.AUTO.string(
              "  @|green ✓|@ workspace import: "
                  + importReport.imported()
                  + " imported, "
                  + importReport.skipped()
                  + " skipped (already present)"));
      for (var note : importReport.notes()) {
        System.out.println(note);
      }
    }
    for (var run : runs) {
      if (run.alreadyApplied()) {
        System.out.println(Ansi.AUTO.string("    @|faint " + run.name() + ": already applied|@"));
        continue;
      }
      var report = run.report();
      System.out.println(
          Ansi.AUTO.string(
              "  @|green ✓|@ "
                  + run.name()
                  + ": "
                  + report.applied()
                  + " applied, "
                  + report.ambiguous()
                  + " ambiguous, "
                  + report.skipped()
                  + " skipped"));
      for (var note : report.notes()) {
        System.out.println(note);
      }
    }
  }

  private static DataMigration.Prompter ttyPrompter() {
    if (System.console() == null) {
      return DataMigration.Prompter.NON_INTERACTIVE;
    }
    var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    return (context, candidates) -> {
      System.out.println();
      System.out.println("  " + context + " could belong to:");
      for (var i = 0; i < candidates.size(); i++) {
        System.out.println("    " + (i + 1) + ") " + candidates.get(i));
      }
      System.out.print("  Pick 1-" + candidates.size() + " (Enter to skip): ");
      try {
        var line = reader.readLine();
        if (line == null || line.isBlank()) {
          return Optional.empty();
        }
        var idx = Integer.parseInt(line.trim()) - 1;
        if (idx < 0 || idx >= candidates.size()) {
          return Optional.empty();
        }
        return Optional.of(candidates.get(idx));
      } catch (Exception e) {
        return Optional.empty();
      }
    };
  }
}
