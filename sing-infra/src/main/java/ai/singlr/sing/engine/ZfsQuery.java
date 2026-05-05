/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Queries ZFS pool usage from {@code zpool list}. Pure query utility — no side effects beyond the
 * shell command.
 */
public final class ZfsQuery {

  private ZfsQuery() {}

  /** ZFS pool usage statistics. */
  public record PoolUsage(String size, String allocated, String free, String capacityPercent) {}

  /**
   * Queries ZFS pool usage. Returns {@code null} if the query fails or the output cannot be parsed.
   *
   * @param shell the shell executor
   * @param poolName the ZFS pool name (e.g. "devpool")
   * @return pool usage or null on failure
   */
  public static PoolUsage queryPool(ShellExec shell, String poolName)
      throws IOException, InterruptedException, TimeoutException {
    var result = shell.exec(List.of("zpool", "list", poolName, "-H", "-o", "size,alloc,free,cap"));
    if (!result.ok()) {
      return null;
    }
    var parts = result.stdout().strip().split("\\s+");
    if (parts.length < 4) {
      return null;
    }
    return new PoolUsage(parts[0], parts[1], parts[2], parts[3]);
  }
}
