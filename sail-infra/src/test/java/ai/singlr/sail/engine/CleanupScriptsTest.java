/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CleanupScriptsTest {

  @Test
  void containerCleanupScriptFiltersOnRestartPolicy() {
    var script = CleanupScripts.containerCleanupScript();

    assertTrue(script.contains("restart-policy="));
    assertTrue(script.contains("podman ps -a"));
    assertTrue(script.contains("podman rm -f"));
    assertTrue(script.contains("podman system prune -f"));
  }

  @Test
  void containerCleanupScriptUsesOneHourThreshold() {
    var script = CleanupScripts.containerCleanupScript();

    assertTrue(script.contains("MAX_AGE_SECONDS=3600"));
  }

  @Test
  void containerCleanupScriptIsValidBash() {
    var script = CleanupScripts.containerCleanupScript();

    assertTrue(script.startsWith("#!/bin/bash\n"));
  }

  @Test
  void cronLineRunsEvery15Minutes() {
    var cron = CleanupScripts.cronLine();

    assertTrue(cron.contains(CleanupScripts.CONTAINER_CLEANUP_PATH));
    assertTrue(cron.startsWith("*/15 * * * *"), "cleanup sweeps every 15 minutes");
    assertTrue(cron.endsWith("\n"));
  }

  @Test
  void legacyCronPatternMatchesOldPruneCommand() {
    var legacy = CleanupScripts.legacyCronPattern();
    var oldCron = "0 * * * * podman system prune -f --filter \"until=1h\" >/dev/null 2>&1";

    assertTrue(oldCron.contains(legacy));
  }

  @Test
  void containerCleanupHandlesStoppedContainers() {
    var script = CleanupScripts.containerCleanupScript();

    assertTrue(script.contains("podman ps -a"), "Must use -a flag to include stopped containers");
  }

  @Test
  void pathConstantsAreConsistent() {
    assertTrue(CleanupScripts.CONTAINER_CLEANUP_PATH.startsWith(CleanupScripts.SAIL_DIR));
    assertTrue(CleanupScripts.CONTAINER_CLEANUP_PATH.endsWith("cleanup-containers.sh"));
  }

  @Test
  void buildUpgradedCrontabRemovesLegacyLine() {
    var oldCron = "0 * * * * podman system prune -f --filter \"until=1h\" >/dev/null 2>&1\n";

    var result = CleanupScripts.buildUpgradedCrontab(oldCron);

    assertFalse(result.contains("podman system prune"));
    assertTrue(result.contains(CleanupScripts.CONTAINER_CLEANUP_PATH));
  }

  @Test
  void buildUpgradedCrontabPreservesOtherEntries() {
    var oldCron = "30 2 * * * /usr/local/bin/backup.sh\n0 * * * * podman system prune -f\n";

    var result = CleanupScripts.buildUpgradedCrontab(oldCron);

    assertTrue(result.contains("backup.sh"));
    assertFalse(result.contains("podman system prune"));
    assertTrue(result.contains(CleanupScripts.CONTAINER_CLEANUP_PATH));
  }

  @Test
  void buildUpgradedCrontabFromEmpty() {
    var result = CleanupScripts.buildUpgradedCrontab("");

    assertTrue(result.contains(CleanupScripts.CONTAINER_CLEANUP_PATH));
    assertTrue(result.startsWith("*/15 * * * *"));
  }

  @Test
  void buildUpgradedCrontabReplacesAnOlderCadenceWithoutDuplicating() {
    var hourly = "0 * * * * " + CleanupScripts.CONTAINER_CLEANUP_PATH + " >/dev/null 2>&1\n";

    var result = CleanupScripts.buildUpgradedCrontab(hourly);

    assertFalse(result.contains("0 * * * * "), "the old hourly cadence line is removed");
    assertTrue(result.contains("*/15 * * * * "), "replaced with the 15-minute cadence");
    assertEquals(
        1,
        result.lines().filter(l -> l.contains(CleanupScripts.CONTAINER_CLEANUP_PATH)).count(),
        "exactly one cleanup cron line, never a duplicate");
  }

  @Test
  void buildUpgradedCrontabIsIdempotent() {
    var once = CleanupScripts.buildUpgradedCrontab("");

    assertEquals(once, CleanupScripts.buildUpgradedCrontab(once), "re-applying converges");
  }
}
