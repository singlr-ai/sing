/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.store;

import ai.singlr.sail.config.Spec;
import ai.singlr.sail.config.YamlUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Imports file-based specs (specs/&lt;id&gt;/spec.yaml + spec.md) into the SQLite database.
 * Idempotent: skips specs whose ID already exists in the database.
 */
public final class SpecMigrator {

  /** Bucket assigned to imported specs that don't declare a {@code project:} key. */
  public static final String UNASSIGNED_PROJECT = "unassigned";

  private final SpecStore store;

  public SpecMigrator(SpecStore store) {
    this.store = store;
  }

  public record MigrationResult(int imported, int skipped, List<String> errors) {}

  public MigrationResult importFromDirectory(Path specsDir) {
    return importFromDirectory(specsDir, UNASSIGNED_PROJECT);
  }

  /**
   * Imports specs from {@code specsDir}. Each spec whose {@code spec.yaml} omits {@code project:}
   * is assigned to {@code defaultProject}, so the FDE can re-bucket later via {@code sail spec
   * edit}.
   */
  public MigrationResult importFromDirectory(Path specsDir, String defaultProject) {
    if (!Files.isDirectory(specsDir)) {
      return new MigrationResult(0, 0, List.of());
    }

    var imported = 0;
    var skipped = 0;
    var errors = new ArrayList<String>();

    try (var dirs = Files.list(specsDir)) {
      var specDirs = dirs.filter(Files::isDirectory).sorted().toList();
      for (var specDir : specDirs) {
        var specYaml = specDir.resolve("spec.yaml");
        if (!Files.exists(specYaml)) continue;

        var specId = specDir.getFileName().toString();
        try {
          if (importFromDirectory(specDir, specId, defaultProject)) {
            imported++;
          } else {
            skipped++;
          }
        } catch (Exception e) {
          errors.add(specId + ": " + e.getMessage());
        }
      }
    } catch (IOException e) {
      errors.add("Failed to list specs directory: " + e.getMessage());
    }

    return new MigrationResult(imported, skipped, List.copyOf(errors));
  }

  /**
   * Imports an already-parsed spec read from any source (filesystem or container shell). Skips —
   * returning {@code false} — when a spec with the same id already exists. Specs that omit {@code
   * project:} are bucketed under {@code defaultProject}.
   */
  public boolean importSpec(Spec spec, String defaultProject, String body, String plan) {
    if (spec.id() == null || spec.id().isBlank()) {
      throw new IllegalArgumentException("Spec is missing an id.");
    }
    if (store.findById(spec.id()).isPresent()) {
      return false;
    }
    var project = spec.project() != null ? spec.project() : defaultProject;
    var dependsOn = spec.dependsOn() != null ? spec.dependsOn() : List.<String>of();
    var repos = spec.repos() != null ? spec.repos() : List.<String>of();

    var row =
        new SpecStore.SpecRow(
            spec.id(),
            project,
            spec.title(),
            mapLegacyStatus(spec.status()),
            spec.assignee(),
            spec.agent(),
            spec.model(),
            spec.reasoningEffort(),
            spec.branch(),
            0,
            spec.assignee(),
            "",
            "",
            dependsOn,
            repos);
    store.create(row);

    var safeBody = Objects.requireNonNullElse(body, "");
    var safePlan = Objects.requireNonNullElse(plan, "");
    if (!safeBody.isEmpty() || !safePlan.isEmpty()) {
      store.setContent(spec.id(), safeBody, safePlan);
    }
    return true;
  }

  private boolean importFromDirectory(Path specDir, String specId, String defaultProject)
      throws IOException {
    var map = YamlUtil.parseFile(specDir.resolve("spec.yaml"));
    map.putIfAbsent("id", specId);
    var spec = Spec.fromMap(map);

    var specMd = specDir.resolve("spec.md");
    var planMd = specDir.resolve("plan.md");
    var body = Files.exists(specMd) ? Files.readString(specMd) : "";
    var plan = Files.exists(planMd) ? Files.readString(planMd) : "";
    return importSpec(spec, defaultProject, body, plan);
  }

  private static String mapLegacyStatus(String status) {
    return switch (status) {
      case "pending", "in_progress", "review", "done" -> status;
      case "archive", "archived" -> "archived";
      default -> "draft";
    };
  }
}
