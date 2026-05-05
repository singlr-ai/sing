/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Queries host filesystem usage via {@code df} for the Incus storage directory. Used when the
 * storage backend is {@code dir} (no ZFS pool to query).
 */
public final class DirQuery {

  private DirQuery() {}

  /** Filesystem usage statistics. */
  public record FsUsage(String size, String used, String available, String usePercent) {}

  /**
   * Queries filesystem usage for the Incus storage directory. Returns {@code null} if the query
   * fails or the output cannot be parsed.
   */
  public static FsUsage queryFilesystem(ShellExec shell)
      throws IOException, InterruptedException, TimeoutException {
    var result =
        shell.exec(List.of("df", "-h", "--output=size,used,avail,pcent", "/var/lib/incus"));
    if (!result.ok()) {
      return null;
    }
    var lines = result.stdout().strip().split("\n");
    if (lines.length < 2) {
      return null;
    }
    var parts = lines[1].strip().split("\\s+");
    if (parts.length < 4) {
      return null;
    }
    return new FsUsage(parts[0], parts[1], parts[2], parts[3]);
  }
}
