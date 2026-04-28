/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import ai.singlr.sing.config.YamlUtil;
import ai.singlr.sing.engine.Banner;
import ai.singlr.sing.engine.ContainerManager;
import ai.singlr.sing.engine.ShellExecutor;
import ai.singlr.sing.engine.SnapshotManager;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Deletes old snapshots across one or all projects. Useful for reclaiming disk when auto-snapshot
 * creates snapshots on every dispatch.
 */
@Command(
    name = "prune",
    description = "Delete snapshots older than a given age.",
    mixinStandardHelpOptions = true)
public final class SnapsPruneCommand implements Runnable {

  private static final Pattern AGE_PATTERN = Pattern.compile("(\\d+)([dhm])");

  @Parameters(
      index = "0",
      arity = "0..1",
      description = "Project name. Omit to prune all projects.")
  private String name;

  @Option(
      names = "--older-than",
      required = true,
      description = "Delete snapshots older than this age. Examples: 7d, 24h, 30d")
  private String olderThan;

  @Option(names = "--dry-run", description = "Print what would be deleted without deleting.")
  private boolean dryRun;

  @Option(names = "--json", description = "Output in JSON format.")
  private boolean json;

  @picocli.CommandLine.Spec private CommandSpec commandSpec;

  @Override
  public void run() {
    try {
      execute();
    } catch (Exception e) {
      var msg = Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName());
      System.err.println(Banner.errorLine(msg, Ansi.AUTO));
      throw new picocli.CommandLine.ExecutionException(commandSpec.commandLine(), msg, e);
    }
  }

  private void execute() throws Exception {
    var maxAge = parseAge(olderThan);
    var cutoff = Instant.now().minus(maxAge);
    var shell = new ShellExecutor(dryRun);
    var mgr = new ContainerManager(shell);
    var snapMgr = new SnapshotManager(shell);
    var ansi = Ansi.AUTO;

    List<String> targets;
    if (name != null) {
      targets = List.of(name);
    } else {
      targets = mgr.listAll().stream().map(ContainerManager.ContainerInfo::name).toList();
    }

    if (!json) {
      Banner.printBranding(System.out, ansi);
      System.out.println();
      var scope = name != null ? name : "all projects";
      var mode = dryRun ? " (dry run)" : "";
      System.out.println(
          ansi.string(
              "  @|bold Pruning snapshots|@ older than " + olderThan + " from " + scope + mode));
      System.out.println();
    }

    var totalDeleted = 0;
    var totalKept = 0;
    var projectResults = new ArrayList<LinkedHashMap<String, Object>>();

    for (var project : targets) {
      List<SnapshotManager.SnapshotInfo> snapshots;
      try {
        snapshots = snapMgr.list(project);
      } catch (Exception e) {
        if (!json) {
          System.err.println(
              Banner.errorLine(
                  "Could not list snapshots for '"
                      + project
                      + "': "
                      + e.getMessage()
                      + ". Skipping.",
                  ansi));
        }
        continue;
      }
      if (snapshots.isEmpty()) {
        continue;
      }

      var deleted = 0;
      var kept = 0;
      for (var snap : snapshots) {
        var snapTime = parseSnapshotTime(snap.createdAt());
        if (snapTime != null && snapTime.isBefore(cutoff)) {
          if (!json) {
            var action = dryRun ? "would delete" : "deleting";
            System.out.println(ansi.string("  [" + action + "] " + project + "/" + snap.name()));
          }
          if (!dryRun) {
            snapMgr.delete(project, snap.name());
          }
          deleted++;
        } else {
          kept++;
        }
      }

      totalDeleted += deleted;
      totalKept += kept;

      if (json && deleted > 0) {
        var result = new LinkedHashMap<String, Object>();
        result.put("project", project);
        result.put("deleted", deleted);
        result.put("kept", kept);
        projectResults.add(result);
      }
    }

    if (json) {
      var map = new LinkedHashMap<String, Object>();
      map.put("older_than", olderThan);
      map.put("dry_run", dryRun);
      map.put("total_deleted", totalDeleted);
      map.put("total_kept", totalKept);
      map.put("projects", projectResults);
      System.out.println(YamlUtil.dumpJson(map));
      return;
    }

    System.out.println();
    var verb = dryRun ? "Would delete" : "Deleted";
    System.out.println(
        ansi.string(
            "  @|bold,green \u2713|@ "
                + verb
                + " "
                + totalDeleted
                + " snapshot(s), kept "
                + totalKept));
  }

  static Duration parseAge(String value) {
    var matcher = AGE_PATTERN.matcher(value.strip());
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "Invalid age format: '"
              + value
              + "'. Use a number followed by d (days), h (hours), or m (minutes)."
              + " Examples: 7d, 24h, 30d");
    }
    var amount = Long.parseLong(matcher.group(1));
    return switch (matcher.group(2)) {
      case "d" -> Duration.ofDays(amount);
      case "h" -> Duration.ofHours(amount);
      case "m" -> Duration.ofMinutes(amount);
      default -> throw new IllegalArgumentException("Unknown unit: " + matcher.group(2));
    };
  }

  static Instant parseSnapshotTime(String iso) {
    if (iso == null || iso.isBlank()) {
      return null;
    }
    try {
      return OffsetDateTime.parse(iso).toInstant();
    } catch (DateTimeParseException ignored) {
      try {
        return Instant.parse(iso);
      } catch (DateTimeParseException ignored2) {
        return null;
      }
    }
  }
}
