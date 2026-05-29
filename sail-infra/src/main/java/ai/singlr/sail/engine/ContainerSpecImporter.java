/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.engine;

import ai.singlr.sail.config.SailYaml;
import ai.singlr.sail.config.YamlUtil;
import ai.singlr.sail.store.SpecMigrator;
import ai.singlr.sail.store.SpecStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Imports pre-control-plane file-based specs into the control-plane database. The control plane
 * runs on the host, but project specs live at {@code /home/<sshUser>/workspace/<specsDir>/} inside
 * each project's Incus container — a filesystem the host cannot read directly. So this reads them
 * the same way every other command does: through the container shell ({@link SpecWorkspace} over
 * {@code incus exec}), bucketing each spec under its project name.
 *
 * <p>Idempotent — {@link SpecMigrator} skips by id. Driven only by {@code sail migrate}; the daemon
 * never walks containers.
 */
public final class ContainerSpecImporter {

  /**
   * Per-run summary surfaced to {@code sail migrate}.
   *
   * @param imported newly-inserted specs across all containers
   * @param skipped specs already in the DB (skip-by-id)
   * @param notes one line per project the scan touched (or skipped)
   */
  public record Report(int imported, int skipped, List<String> notes) {
    public static Report empty() {
      return new Report(0, 0, List.of());
    }
  }

  private final ShellExec shell;
  private final ContainerManager containers;
  private final SpecMigrator migrator;
  private final Path projectsDir;

  public ContainerSpecImporter(
      ShellExec shell, ContainerManager containers, SpecStore store, Path projectsDir) {
    this.shell = Objects.requireNonNull(shell);
    this.containers = Objects.requireNonNull(containers);
    this.migrator = new SpecMigrator(store);
    this.projectsDir = Objects.requireNonNull(projectsDir);
  }

  public Report importAll() {
    if (!Files.isDirectory(projectsDir)) {
      return Report.empty();
    }
    List<Path> projectDirs;
    try (var stream = Files.list(projectsDir)) {
      projectDirs = stream.filter(Files::isDirectory).sorted().toList();
    } catch (IOException e) {
      return new Report(0, 0, List.of("  • Failed to list projects: " + e.getMessage()));
    }

    var imported = 0;
    var skipped = 0;
    var notes = new ArrayList<String>();
    for (var projectDir : projectDirs) {
      var project = projectDir.getFileName().toString();
      var descriptor = projectDir.resolve(SailPaths.PROJECT_DESCRIPTOR);
      if (!Files.isRegularFile(descriptor)) {
        continue;
      }
      try {
        var report = importProject(project, descriptor);
        imported += report.imported();
        skipped += report.skipped();
        notes.addAll(report.notes());
      } catch (Exception e) {
        notes.add("  • " + project + " ERROR: " + e.getMessage());
      }
    }
    return new Report(imported, skipped, List.copyOf(notes));
  }

  private Report importProject(String project, Path descriptor) throws Exception {
    var config = SailYaml.fromMap(YamlUtil.parseFile(descriptor));
    if (config.agent() == null || config.agent().specsDir() == null) {
      return Report.empty();
    }
    var state = containers.queryState(project);
    if (!(state instanceof ContainerState.Running)) {
      return new Report(0, 0, List.of("  • " + project + ": skipped (" + describe(state) + ")"));
    }

    var specsDir = "/home/" + config.sshUser() + "/workspace/" + config.agent().specsDir();
    var workspace = new SpecWorkspace(shell, project, specsDir);
    var specs = workspace.readSpecs();
    if (specs.isEmpty()) {
      return Report.empty();
    }

    var imported = 0;
    var skipped = 0;
    for (var spec : specs) {
      var body = workspace.readSpecBody(spec.id());
      var plan = workspace.readPlanBody(spec.id());
      if (migrator.importSpec(spec, project, body, plan)) {
        imported++;
      } else {
        skipped++;
      }
    }
    return new Report(
        imported,
        skipped,
        List.of(
            "  • "
                + project
                + ": imported "
                + imported
                + ", skipped (already present) "
                + skipped
                + " from "
                + specsDir));
  }

  private static String describe(ContainerState state) {
    return switch (state) {
      case ContainerState.Running ignored -> "running";
      case ContainerState.Stopped ignored -> "container stopped";
      case ContainerState.NotCreated ignored -> "container not created";
      case ContainerState.Error error -> "container error: " + error.message();
    };
  }
}
