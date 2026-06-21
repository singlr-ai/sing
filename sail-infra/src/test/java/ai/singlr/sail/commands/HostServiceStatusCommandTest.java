/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.singlr.sail.engine.SystemdServiceInstaller.ServiceStatus;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HostServiceStatusCommandTest {

  @Test
  void systemModeOmitsTheSystemdLinkInsteadOfThrowing() {
    var map =
        HostServiceStatusCommand.statusMap(
            true, Path.of("/etc/systemd/system/sail-api.service"), null, "root", "n/a", null);

    assertFalse(map.containsKey("systemd_link"), "SYSTEM mode has no per-user link");
    assertEquals("/etc/systemd/system/sail-api.service", map.get("service_file"));
    assertEquals(true, map.get("installed"));
  }

  @Test
  void userModeIncludesTheSystemdLinkAndRunningStatus() {
    var status = new ServiceStatus("loaded", "active", "running", 4242);
    var map =
        HostServiceStatusCommand.statusMap(
            true,
            Path.of("/home/dev/.config/systemd/user/sail-api.service"),
            Path.of("/home/dev/.config/systemd/user/sail-api.service"),
            "dev",
            "enabled",
            status);

    assertTrue(map.containsKey("systemd_link"));
    assertEquals(true, map.get("running"));
    assertEquals(4242, map.get("pid"));
  }

  @Test
  void notInstalledOmitsStatusFields() {
    var map =
        HostServiceStatusCommand.statusMap(
            false, Path.of("/etc/systemd/system/sail-api.service"), null, "root", "n/a", null);

    assertFalse(map.containsKey("running"));
    assertFalse(map.containsKey("pid"));
  }
}
