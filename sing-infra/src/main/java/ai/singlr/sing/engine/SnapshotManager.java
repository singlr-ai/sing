/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import ai.singlr.sing.config.YamlUtil;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * Manages Incus container snapshots: create, restore, list, delete. All state is queried live from
 * Incus — no local caching. Used by {@code snap}, {@code restore}, {@code snaps}, and {@code agent}
 * commands.
 */
public final class SnapshotManager {

  private static final DateTimeFormatter LABEL_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  private final ShellExec shell;

  public SnapshotManager(ShellExec shell) {
    this.shell = shell;
  }

  /** Metadata for a single Incus snapshot. */
  public record SnapshotInfo(String name, String createdAt) {}

  /** Creates a snapshot of the given container with the specified label. */
  public void create(String containerName, String label)
      throws IOException, InterruptedException, TimeoutException {
    var result = shell.exec(List.of("incus", "snapshot", "create", containerName, label));
    if (!result.ok()) {
      throw new IOException(
          "Failed to create snapshot '"
              + label
              + "' for '"
              + containerName
              + "': "
              + result.stderr());
    }
  }

  /** Restores a container to the specified snapshot label. */
  public void restore(String containerName, String label)
      throws IOException, InterruptedException, TimeoutException {
    var result = shell.exec(List.of("incus", "snapshot", "restore", containerName, label));
    if (!result.ok()) {
      throw new IOException(
          "Failed to restore snapshot '"
              + label
              + "' for '"
              + containerName
              + "': "
              + result.stderr());
    }
  }

  /** Lists all snapshots for a container, parsed from JSON. Returns empty list if none exist. */
  public List<SnapshotInfo> list(String containerName)
      throws IOException, InterruptedException, TimeoutException {
    var result =
        shell.exec(List.of("incus", "snapshot", "list", containerName, "--format", "json"));
    if (!result.ok()) {
      throw new IOException(
          "Failed to list snapshots for '" + containerName + "': " + result.stderr());
    }
    var entries = YamlUtil.parseList(result.stdout());
    return entries.stream().map(SnapshotManager::parseSnapshotInfo).toList();
  }

  /** Deletes a specific snapshot. */
  public void delete(String containerName, String label)
      throws IOException, InterruptedException, TimeoutException {
    var result = shell.exec(List.of("incus", "snapshot", "delete", containerName, label));
    if (!result.ok()) {
      throw new IOException(
          "Failed to delete snapshot '"
              + label
              + "' for '"
              + containerName
              + "': "
              + result.stderr());
    }
  }

  /** Generates a default snapshot label from the current timestamp. */
  public static String defaultLabel() {
    return "snap-" + LocalDateTime.now().format(LABEL_FORMAT);
  }

  private static SnapshotInfo parseSnapshotInfo(Map<String, Object> entry) {
    var name = Objects.toString(entry.get("name"), "unknown");
    var createdAt = Objects.toString(entry.get("created_at"), "");
    return new SnapshotInfo(name, createdAt);
  }
}
