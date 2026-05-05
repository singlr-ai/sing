/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.commands;

import static org.junit.jupiter.api.Assertions.*;

import ai.singlr.sail.SailVersion;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class UpgradeCommandTest {

  @Test
  void helpTextIncludesOptions() {
    var cmd = new CommandLine(new UpgradeCommand());
    var usage = cmd.getUsageMessage();

    assertTrue(usage.contains("upgrade"));
    assertTrue(usage.contains("--check"));
    assertTrue(usage.contains("--target"));
    assertTrue(usage.contains("--dry-run"));
    assertTrue(usage.contains("--json"));
  }

  @Test
  void versionFlagOutputStartsWithSail() {
    var version = SailVersion.version();
    assertNotNull(version);
    assertFalse(version.isBlank());

    var provider = new SailVersion();
    var lines = provider.getVersion();
    assertTrue(lines[0].startsWith("sail "));
  }
}
