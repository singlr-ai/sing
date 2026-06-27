/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.engine;

/**
 * Generates {@code cleanup-containers.sh}: an automated hourly cron job that force-removes stray
 * Podman containers (Testcontainers leftovers, abandoned builds) while preserving infrastructure
 * services. Uses restart-policy as the discriminator: services created by {@code sail} use {@code
 * --restart=always}, while test containers do not.
 */
public final class CleanupScripts {

  static final String SAIL_DIR = "/home/dev/.sail";
  static final String CONTAINER_CLEANUP_PATH = SAIL_DIR + "/cleanup-containers.sh";

  private CleanupScripts() {}

  /**
   * Generates the automated container cleanup script. Identifies stray containers by filtering on
   * restart policy: containers with {@code restart-policy=always} are infrastructure services and
   * are never touched. All other containers (Testcontainers, ad-hoc runs) older than 1 hour are
   * force-removed. Also prunes stopped containers and dangling images.
   */
  public static String containerCleanupScript() {
    return """
        #!/bin/bash
        MAX_AGE_SECONDS=3600

        stray_ids=$(podman ps -a --filter restart-policy= --format '{{.ID}} {{.CreatedAt}}' 2>/dev/null)
        if [ -n "$stray_ids" ]; then
            now=$(date +%s)
            while IFS= read -r line; do
                [ -z "$line" ] && continue
                id="${line%% *}"
                created_str="${line#* }"
                created_epoch=$(date -d "$created_str" +%s 2>/dev/null) || continue
                age=$((now - created_epoch))
                if [ "$age" -gt "$MAX_AGE_SECONDS" ]; then
                    podman rm -f "$id" >/dev/null 2>&1
                fi
            done <<< "$stray_ids"
        fi

        podman system prune -f >/dev/null 2>&1
        """;
  }

  /**
   * Returns the cron line that invokes the automated container cleanup script hourly. Replaces the
   * legacy {@code podman system prune} cron line.
   */
  public static String cronLine() {
    return "0 * * * * " + CONTAINER_CLEANUP_PATH + " >/dev/null 2>&1\n";
  }

  /**
   * Builds an upgraded crontab by removing legacy {@code podman system prune} lines and appending
   * the new cleanup script invocation.
   *
   * @param existingCron the current crontab content (may be empty)
   * @return the updated crontab content
   */
  public static String buildUpgradedCrontab(String existingCron) {
    var cleaned =
        existingCron
            .lines()
            .filter(l -> !l.contains(legacyCronPattern()))
            .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
    return (cleaned.isEmpty() ? "" : cleaned + "\n") + cronLine();
  }

  /**
   * Returns the legacy cron pattern used by older versions, for detection and replacement during
   * upgrades.
   */
  static String legacyCronPattern() {
    return "podman system prune";
  }
}
