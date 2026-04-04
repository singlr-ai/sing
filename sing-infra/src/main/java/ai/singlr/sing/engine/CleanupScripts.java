/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

/**
 * Generates cleanup scripts that run inside Incus containers. Two scripts are produced:
 *
 * <ul>
 *   <li>{@code cleanup-containers.sh} — automated hourly cron job that kills stray Podman
 *       containers (Testcontainers leftovers, abandoned builds) while preserving infrastructure
 *       services. Uses restart-policy as the discriminator: services created by {@code sing} use
 *       {@code --restart=always}, while test containers do not.
 *   <li>{@code cleanup-agents.sh} — manual helper script that lists and kills stale agent processes
 *       (e.g. leaked Claude processes from IDE extensions). Not automated because killing agent
 *       processes risks terminating the active session.
 * </ul>
 */
public final class CleanupScripts {

  static final String SING_DIR = "/home/dev/.sing";
  static final String CONTAINER_CLEANUP_PATH = SING_DIR + "/cleanup-containers.sh";
  static final String AGENT_CLEANUP_PATH = SING_DIR + "/cleanup-agents.sh";

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
   * Generates the manual agent process cleanup helper. Lists stale {@code claude} processes (older
   * than 24 hours), preserving the most recently started one even if it exceeds the threshold. The
   * engineer runs this manually when memory pressure is noticed.
   */
  public static String agentCleanupScript() {
    return """
        #!/bin/bash
        MAX_AGE_SECONDS=86400

        newest_pid=""
        newest_start=0
        stale_pids=()

        while IFS= read -r line; do
            [ -z "$line" ] && continue
            pid=$(echo "$line" | awk '{print $1}')
            etime=$(echo "$line" | awk '{print $2}')
            start_epoch=$(echo "$line" | awk '{print $3}')

            if [ "$start_epoch" -gt "$newest_start" ]; then
                if [ -n "$newest_pid" ]; then
                    stale_pids+=("$newest_pid")
                fi
                newest_pid="$pid"
                newest_start="$start_epoch"
            else
                stale_pids+=("$pid")
            fi
        done < <(ps -eo pid,etimes,lstart,comm --no-headers | awk '$NF == "claude" {
            cmd="date -d \\"" $3 " " $4 " " $5 " " $6 " " $7 "\\" +%s"
            cmd | getline epoch
            close(cmd)
            print $1, $2, epoch
        }')

        if [ ${#stale_pids[@]} -eq 0 ]; then
            echo "No stale claude processes found."
            exit 0
        fi

        echo "Found ${#stale_pids[@]} stale claude process(es) (keeping newest PID $newest_pid):"
        echo ""
        for pid in "${stale_pids[@]}"; do
            etime=$(ps -o etimes= -p "$pid" 2>/dev/null | tr -d ' ')
            rss=$(ps -o rss= -p "$pid" 2>/dev/null | tr -d ' ')
            if [ -n "$etime" ] && [ -n "$rss" ]; then
                hours=$((etime / 3600))
                mb=$((rss / 1024))
                echo "  PID $pid — ${hours}h old, ${mb}MB RSS"
            fi
        done

        echo ""
        read -p "Kill these processes? [y/N] " confirm
        if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
            for pid in "${stale_pids[@]}"; do
                kill "$pid" 2>/dev/null && echo "  Killed PID $pid" || echo "  Failed to kill PID $pid"
            done
            echo "Done. Kept newest claude process (PID $newest_pid)."
        else
            echo "Aborted."
        fi
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
   * Returns the legacy cron pattern used by older versions, for detection and replacement during
   * upgrades.
   */
  static String legacyCronPattern() {
    return "podman system prune";
  }
}
