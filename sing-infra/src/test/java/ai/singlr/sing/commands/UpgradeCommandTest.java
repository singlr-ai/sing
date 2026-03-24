/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import static org.junit.jupiter.api.Assertions.*;

import ai.singlr.sing.SingVersion;
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
  void versionFlagOutputStartsWithSing() {
    var version = SingVersion.version();
    assertNotNull(version);
    assertFalse(version.isBlank());

    var provider = new SingVersion();
    var lines = provider.getVersion();
    assertTrue(lines[0].startsWith("sing "));
  }
}
