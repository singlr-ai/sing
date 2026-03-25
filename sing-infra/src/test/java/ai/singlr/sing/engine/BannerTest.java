/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import static org.junit.jupiter.api.Assertions.*;

import ai.singlr.sing.config.HostYaml;
import ai.singlr.sing.config.SingYaml;
import ai.singlr.sing.config.Spec;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.Help.Ansi;

class BannerTest {

  private static final HostDetector.HostInfo SUPPORTED =
      new HostDetector.HostInfo(
          "singular-main", "ubuntu", "24.04", "Ubuntu 24.04.1 LTS", 6, 12, 31868, true);

  private static final HostDetector.HostInfo UNSUPPORTED =
      new HostDetector.HostInfo("my-host", "fedora", "41", "Fedora Linux 41", 8, 8, 16384, false);

  @Test
  void bannerContainsHostInfo() {
    var output = renderBanner(SUPPORTED);

    assertTrue(output.contains("singular-main"));
    assertTrue(output.contains("Ubuntu 24.04.1 LTS"));
    assertTrue(output.contains("6 cores / 12 threads"));
    assertTrue(output.contains("31,868 MB"));
    assertTrue(output.contains("Host Detection"));
  }

  @Test
  void bannerShowsCheckForSupported() {
    var output = renderBanner(SUPPORTED);

    assertTrue(output.contains("\u2713"));
    assertFalse(output.contains("\u2717"));
  }

  @Test
  void bannerShowsXForUnsupported() {
    var output = renderBanner(UNSUPPORTED);

    assertTrue(output.contains("\u2717"));
    assertTrue(output.contains("Fedora Linux 41"));
  }

  @Test
  void bannerHasNoAnsiCodesWhenOff() {
    var output = renderBanner(SUPPORTED);

    assertFalse(output.contains("\u001b["), "Should not contain ANSI escape codes");
  }

  @Test
  void stepLineFormatsCorrectly() {
    var line = Banner.stepLine(3, 7, "Installing ZFS utilities...", Ansi.OFF);

    assertTrue(line.contains("[3/7]"));
    assertTrue(line.contains("Installing ZFS utilities..."));
  }

  @Test
  void stepDoneLineHasCheckmark() {
    var line = Banner.stepDoneLine(3, 7, "installed", Ansi.OFF);

    assertTrue(line.contains("[3/7]"));
    assertTrue(line.contains("\u2713"));
    assertTrue(line.contains("installed"));
  }

  @Test
  void errorLineHasXMark() {
    var line = Banner.errorLine("Something failed", Ansi.OFF);

    assertTrue(line.contains("\u2717"));
    assertTrue(line.contains("Something failed"));
  }

  @Test
  void unsupportedMessageShowsOsAndSupported() {
    var out = new ByteArrayOutputStream();
    Banner.printUnsupported(UNSUPPORTED, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("Unsupported operating system"));
    assertTrue(output.contains("Fedora Linux 41"));
    assertTrue(output.contains("Ubuntu 24.04+"));
  }

  @Test
  void brandingContainsSingAsciiArt() {
    var out = new ByteArrayOutputStream();
    Banner.printBranding(new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("███████"));
    assertTrue(output.contains("singlr-ai/sing"));
  }

  @Test
  void bannerBordersAlign() {
    var output = renderBanner(SUPPORTED);
    var lines = output.split("\n");

    for (var line : lines) {
      var pipeCount = line.chars().filter(c -> c == '\u2502').count();
      if (pipeCount > 0) {
        assertEquals(2, pipeCount, "Misaligned borders in: " + line);
      }
    }
  }

  @Test
  void bannerWithNullHostname() {
    var info = new HostDetector.HostInfo(null, "ubuntu", "24.04", null, 4, 4, 8192, true);
    var output = renderBanner(info);

    assertTrue(output.contains("Host Detection"));
    assertTrue(output.contains("4 cores"));
    assertTrue(output.contains("8,192 MB"));
  }

  @Test
  void bannerUnsupportedBordersAlign() {
    var output = renderBanner(UNSUPPORTED);
    var lines = output.split("\n");

    for (var line : lines) {
      var pipeCount = line.chars().filter(c -> c == '\u2502').count();
      if (pipeCount > 0) {
        assertEquals(2, pipeCount, "Misaligned borders in: " + line);
      }
    }
  }

  @Test
  void rootRequiredMessage() {
    var out = new ByteArrayOutputStream();
    Banner.printRootRequired(new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("Root privileges required"));
    assertTrue(output.contains("sudo sing host init"));
  }

  @Test
  void bannerWithLongHostname() {
    var info =
        new HostDetector.HostInfo(
            "very-long-hostname-that-might-push-limits",
            "ubuntu",
            "24.04",
            "Ubuntu 24.04.1 LTS",
            32,
            64,
            131072,
            true);
    var output = renderBanner(info);

    var lines = output.split("\n");
    for (var line : lines) {
      var pipeCount = line.chars().filter(c -> c == '\u2502').count();
      if (pipeCount > 0) {
        assertEquals(2, pipeCount, "Misaligned borders in: " + line);
      }
    }
  }

  @Test
  void bannerWithVeryLongOsNameTriggersMinPadding() {
    var info =
        new HostDetector.HostInfo(
            "host",
            "ubuntu",
            "24.04",
            "Ubuntu 24.04.1 LTS with a very long name that exceeds the box width entirely",
            4,
            4,
            8192,
            true);
    var output = renderBanner(info);

    assertTrue(output.contains("Host Detection"));
    assertTrue(output.contains("Ubuntu 24.04.1 LTS with a very long name"));
  }

  @Test
  void prerequisitesAllPresentShowsCheckmarks() {
    var result =
        new PrerequisiteChecker.CheckResult(
            List.of(
                new PrerequisiteChecker.Prerequisite("bash", null, "Shell execution"),
                new PrerequisiteChecker.Prerequisite("curl", "curl", "Downloading signing keys")),
            List.of());
    var out = new ByteArrayOutputStream();
    Banner.printPrerequisites(result, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("Checking prerequisites"));
    assertTrue(output.contains("\u2713"));
    assertTrue(output.contains("bash"));
    assertTrue(output.contains("curl"));
    assertFalse(output.contains("\u2717"));
  }

  @Test
  void prerequisitesMissingShowsXMarks() {
    var result =
        new PrerequisiteChecker.CheckResult(
            List.of(new PrerequisiteChecker.Prerequisite("bash", null, "Shell execution")),
            List.of(
                new PrerequisiteChecker.Prerequisite("curl", "curl", "Downloading signing keys")));
    var out = new ByteArrayOutputStream();
    Banner.printPrerequisites(result, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("\u2713"));
    assertTrue(output.contains("\u2717"));
  }

  @Test
  void missingPrerequisitesSummaryShowsInstallCommand() {
    var missing =
        List.of(
            new PrerequisiteChecker.Prerequisite("curl", "curl", "Downloading signing keys"),
            new PrerequisiteChecker.Prerequisite("gpg", "gnupg", "Verifying signing keys"));
    var out = new ByteArrayOutputStream();
    Banner.printMissingPrerequisites(missing, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("Missing commands:"));
    assertTrue(output.contains("curl, gpg"));
    assertTrue(output.contains("apt-get install -y curl gnupg"));
  }

  @Test
  void missingPrerequisitesWithoutPackageSkipsInstallLine() {
    var missing = List.of(new PrerequisiteChecker.Prerequisite("bash", null, "Shell execution"));
    var out = new ByteArrayOutputStream();
    Banner.printMissingPrerequisites(missing, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("Missing commands:"));
    assertTrue(output.contains("bash"));
    assertFalse(output.contains("apt-get install"));
  }

  @Test
  void stepSkippedLineHasArrow() {
    var line = Banner.stepSkippedLine(2, 7, "already done", Ansi.OFF);

    assertTrue(line.contains("[2/7]"));
    assertTrue(line.contains("\u2192"));
    assertTrue(line.contains("already done"));
  }

  @Test
  void projectSummaryShowsAllFields() {
    var config =
        new SingYaml(
            "acme-health",
            "Acme Health",
            new SingYaml.Resources(4, "12GB", "150GB"),
            "ubuntu/24.04",
            List.of("postgresql-client-16"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new SingYaml.Ssh("dev", List.of("ssh-ed25519 AAAA...")));
    var out = new ByteArrayOutputStream();
    Banner.printProjectSummary(config, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("Project"));
    assertTrue(output.contains("acme-health"));
    assertTrue(output.contains("ubuntu/24.04"));
    assertTrue(output.contains("4 cores"));
    assertTrue(output.contains("12GB"));
    assertTrue(output.contains("150GB"));
    assertTrue(output.contains("1 user + 5 baseline"));
    assertTrue(output.contains("dev"));
  }

  @Test
  void projectSummaryBordersAlign() {
    var config =
        new SingYaml(
            "test-proj",
            null,
            new SingYaml.Resources(2, "4GB", "50GB"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    var out = new ByteArrayOutputStream();
    Banner.printProjectSummary(config, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);
    var lines = output.split("\n");

    for (var line : lines) {
      var pipeCount = line.chars().filter(c -> c == '\u2502').count();
      if (pipeCount > 0) {
        assertEquals(2, pipeCount, "Misaligned borders in: " + line);
      }
    }
  }

  @Test
  void projectCreatedShowsNameAndZedConnect() {
    var out = new ByteArrayOutputStream();
    Banner.printProjectCreated("acme-health", "dev", new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("\u2713"));
    assertTrue(output.contains("acme-health"));
    assertTrue(output.contains("sing connect acme-health"));
    assertTrue(output.contains("sing shell acme-health"));
  }

  @Test
  void projectCreatedWithoutSshDefaultsToDev() {
    var out = new ByteArrayOutputStream();
    Banner.printProjectCreated("simple", null, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("simple"));
    assertTrue(output.contains("sing connect simple"));
    assertTrue(output.contains("sing shell simple"));
  }

  @Test
  void resumeInfoShowsCompletedPhaseAndError() {
    var state =
        new ProvisionState(
            "PACKAGES_INSTALLED",
            "2026-02-18T10:30:00Z",
            "2026-02-18T10:34:22Z",
            new ProvisionState.ProvisionError(
                "SSH_CONFIGURED", "Failed to create user", "2026-02-18T10:34:22Z"));
    var out = new ByteArrayOutputStream();
    Banner.printResumeInfo(state, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("Resuming incomplete provisioning"));
    assertTrue(output.contains("PACKAGES_INSTALLED"));
    assertTrue(output.contains("SSH_CONFIGURED"));
    assertTrue(output.contains("Failed to create user"));
  }

  @Test
  void resumeInfoWithoutErrorShowsOnlyPhase() {
    var state = new ProvisionState("NETWORK_READY", "2026-02-18T10:30:00Z", null, null);
    var out = new ByteArrayOutputStream();
    Banner.printResumeInfo(state, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("NETWORK_READY"));
    assertFalse(output.contains("Prior failure"));
  }

  @Test
  void snapshotTableShowsLabelAndTimestamp() {
    var snapshots =
        List.of(
            new SnapshotManager.SnapshotInfo(
                "snap-20260219-100000", "2026-02-19T10:00:00.123456789Z"),
            new SnapshotManager.SnapshotInfo("pre-agent", "2026-02-19T14:30:22.987654321Z"));
    var out = new ByteArrayOutputStream();
    Banner.printSnapshotTable("acme-health", snapshots, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("Snapshots: acme-health"));
    assertTrue(output.contains("snap-20260219-100000"));
    assertTrue(output.contains("pre-agent"));
    assertTrue(output.contains("2026-02-19 10:00:00"));
    assertTrue(output.contains("2026-02-19 14:30:22"));
  }

  @Test
  void snapshotTableBordersAlign() {
    var snapshots =
        List.of(
            new SnapshotManager.SnapshotInfo("snap-20260219-100000", "2026-02-19T10:00:00Z"),
            new SnapshotManager.SnapshotInfo("short", "2026-01-01T00:00:00Z"));
    var out = new ByteArrayOutputStream();
    Banner.printSnapshotTable("test", snapshots, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    for (var line : output.split("\n")) {
      var pipeCount = line.chars().filter(c -> c == '\u2502').count();
      if (pipeCount > 0) {
        assertEquals(2, pipeCount, "Misaligned borders in: " + line);
      }
    }
  }

  @Test
  void snapshotCreatedShowsCheckmarkAndLabel() {
    var out = new ByteArrayOutputStream();
    Banner.printSnapshotCreated(
        "acme-health", "snap-20260219-100000", new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("\u2713"));
    assertTrue(output.contains("Snapshot created"));
    assertTrue(output.contains("snap-20260219-100000"));
  }

  @Test
  void snapshotRestoredShowsContainerAndLabel() {
    var out = new ByteArrayOutputStream();
    Banner.printSnapshotRestored("acme-health", "pre-agent", new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("\u2713"));
    assertTrue(output.contains("Restored"));
    assertTrue(output.contains("acme-health"));
    assertTrue(output.contains("pre-agent"));
  }

  @Test
  void snapshotTableHandlesNullTimestamp() {
    var snapshots = List.of(new SnapshotManager.SnapshotInfo("test-snap", null));
    var out = new ByteArrayOutputStream();
    Banner.printSnapshotTable("proj", snapshots, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("test-snap"));
    assertTrue(output.contains("-")); // null timestamp → "-"
  }

  @Test
  void snapshotTableWithLongContainerName() {
    var snapshots = List.of(new SnapshotManager.SnapshotInfo("snap-1", "2026-02-19T10:00:00Z"));
    var out = new ByteArrayOutputStream();
    Banner.printSnapshotTable(
        "very-long-project-name-that-might-push-limits", snapshots, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("very-long-project-name"));
    assertTrue(output.contains("snap-1"));
  }

  private static final HostYaml SAMPLE_HOST_YAML =
      new HostYaml(
          "zfs",
          "devpool",
          "/dev/sdb",
          "incusbr0",
          "singlr-base",
          "ubuntu/24.04",
          "6.21",
          null,
          "2026-02-18T01:00:00Z");

  @Test
  void hostStatusShowsAllFields() {
    var poolUsage = new ZfsQuery.PoolUsage("960G", "150G", "810G", "15%");
    var containers =
        List.of(
            new ContainerManager.ContainerInfo(
                "acme", new ContainerState.Running("10.0.0.42"), null),
            new ContainerManager.ContainerInfo("fintech", new ContainerState.Stopped(), null));
    var out = new ByteArrayOutputStream();
    Banner.printHostStatus(
        SAMPLE_HOST_YAML, SUPPORTED, poolUsage, null, containers, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("Host Status"));
    assertTrue(output.contains("singular-main"));
    assertTrue(output.contains("Ubuntu 24.04.1 LTS"));
    assertTrue(output.contains("6 cores / 12 threads"));
    assertTrue(output.contains("31,868 MB"));
    assertTrue(output.contains("devpool on /dev/sdb"));
    assertTrue(output.contains("960G"));
    assertTrue(output.contains("150G (15%)"));
    assertTrue(output.contains("810G"));
    assertTrue(output.contains("6.21"));
    assertTrue(output.contains("2026-02-18 01:00:00"));
    assertTrue(output.contains("2 total (1 running, 1 stopped)"));
  }

  @Test
  void hostStatusBordersAlign() {
    var poolUsage = new ZfsQuery.PoolUsage("960G", "150G", "810G", "15%");
    var containers = List.<ContainerManager.ContainerInfo>of();
    var out = new ByteArrayOutputStream();
    Banner.printHostStatus(
        SAMPLE_HOST_YAML, SUPPORTED, poolUsage, null, containers, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    for (var line : output.split("\n")) {
      var pipeCount = line.chars().filter(c -> c == '\u2502').count();
      if (pipeCount > 0) {
        assertEquals(2, pipeCount, "Misaligned borders in: " + line);
      }
    }
  }

  @Test
  void hostStatusWithNullPoolUsage() {
    var containers = List.<ContainerManager.ContainerInfo>of();
    var out = new ByteArrayOutputStream();
    Banner.printHostStatus(
        SAMPLE_HOST_YAML, SUPPORTED, null, null, containers, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("devpool on /dev/sdb"));
    assertFalse(output.contains("Pool Size:"));
  }

  @Test
  void hostStatusDirBackendShowsAdvisoryNote() {
    var dirHostYaml =
        new HostYaml(
            "dir",
            "devpool",
            null,
            "incusbr0",
            "singlr-base",
            "ubuntu/24.04",
            "6.21",
            null,
            "2026-02-18T01:00:00Z");
    var fsUsage = new DirQuery.FsUsage("894G", "120G", "774G", "14%");
    var containers = List.<ContainerManager.ContainerInfo>of();
    var out = new ByteArrayOutputStream();
    Banner.printHostStatus(
        dirHostYaml, SUPPORTED, null, fsUsage, containers, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("devpool (dir)"));
    assertTrue(output.contains("894G"));
    assertTrue(output.contains("120G (14%)"));
    assertTrue(output.contains("774G"));
    assertTrue(output.contains("advisory"));
    assertFalse(output.contains("Pool:"));
  }

  @Test
  void podmanTableShowsServices() {
    var svc1 = new LinkedHashMap<String, Object>();
    svc1.put("Names", List.of("postgres"));
    svc1.put("Image", "postgres:16");
    svc1.put("Status", "Up 3 hours");
    var svc2 = new LinkedHashMap<String, Object>();
    svc2.put("Names", List.of("meilisearch"));
    svc2.put("Image", "getmeili/meilisearch:latest");
    svc2.put("Status", "Up 3 hours");
    var out = new ByteArrayOutputStream();
    Banner.printPodmanTable("acme", List.of(svc1, svc2), new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("Services: acme"));
    assertTrue(output.contains("postgres"));
    assertTrue(output.contains("postgres:16"));
    assertTrue(output.contains("meilisearch"));
    assertTrue(output.contains("Up 3 hours"));
  }

  @Test
  void podmanTableTruncatesLongImageNames() {
    var svc = new LinkedHashMap<String, Object>();
    svc.put("Names", List.of("redpanda"));
    svc.put("Image", "redpandadata/redpanda:latest");
    svc.put("Status", "Up 1 hour");
    var out = new ByteArrayOutputStream();
    Banner.printPodmanTable("proj", List.of(svc), new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("..."));
    assertTrue(output.contains("redpanda"));
  }

  @Test
  void podmanTableBordersAlign() {
    var svc = new LinkedHashMap<String, Object>();
    svc.put("Names", List.of("postgres"));
    svc.put("Image", "postgres:16");
    svc.put("Status", "Up 3 hours");
    var out = new ByteArrayOutputStream();
    Banner.printPodmanTable("test", List.of(svc), new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    for (var line : output.split("\n")) {
      var pipeCount = line.chars().filter(c -> c == '\u2502').count();
      if (pipeCount > 0) {
        assertEquals(2, pipeCount, "Misaligned borders in: " + line);
      }
    }
  }

  @Test
  void podmanTableHandlesNameAsString() {
    var svc = new LinkedHashMap<String, Object>();
    svc.put("Name", "postgres");
    svc.put("Image", "postgres:16");
    svc.put("State", "running");
    var out = new ByteArrayOutputStream();
    Banner.printPodmanTable("proj", List.of(svc), new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("postgres"));
  }

  @Test
  void sshTunnelsPrintsSinglePort() {
    var out = new ByteArrayOutputStream();
    Banner.printSshTunnels("acme", null, List.of(5432), new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("-L 5432:localhost:5432"));
    assertTrue(output.contains("dev@acme"));
    assertTrue(output.contains("-N"));
  }

  @Test
  void sshTunnelsPrintsMultiplePortsSorted() {
    var out = new ByteArrayOutputStream();
    Banner.printSshTunnels("proj", null, List.of(6379, 5432, 9092), new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("-L 5432:localhost:5432"));
    assertTrue(output.contains("-L 6379:localhost:6379"));
    assertTrue(output.contains("-L 9092:localhost:9092"));
    assertTrue(output.contains("dev@proj"));
    var idx5432 = output.indexOf("5432");
    var idx6379 = output.indexOf("6379");
    var idx9092 = output.indexOf("9092");
    assertTrue(idx5432 < idx6379);
    assertTrue(idx6379 < idx9092);
  }

  @Test
  void sshTunnelsEmptyPortsPrintsNothing() {
    var out = new ByteArrayOutputStream();
    Banner.printSshTunnels("proj", null, List.of(), new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.isEmpty());
  }

  @Test
  void sshTunnelsNullPortsPrintsNothing() {
    var out = new ByteArrayOutputStream();
    Banner.printSshTunnels("proj", null, null, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.isEmpty());
  }

  @Test
  void agentAuthTunnelPrintsPort3000() {
    var out = new ByteArrayOutputStream();
    Banner.printAgentAuthTunnel("acme", new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("-L 3000:localhost:3000"));
    assertTrue(output.contains("acme"));
    assertTrue(output.contains("-N"));
    assertTrue(output.contains("Agent auth"));
  }

  @Test
  void projectConfigShowsAllFields() {
    var services = new LinkedHashMap<String, SingYaml.Service>();
    services.put("postgres", new SingYaml.Service("postgres:16", List.of(5432), null, null, null));
    services.put(
        "meilisearch",
        new SingYaml.Service("getmeili/meilisearch:latest", List.of(7700), null, null, null));
    var config =
        new SingYaml(
            "acme-health",
            "Acme Health Platform",
            new SingYaml.Resources(4, "12GB", "150GB"),
            "ubuntu/24.04",
            List.of("postgresql-client-16"),
            new SingYaml.Runtimes(25, "22", null),
            null,
            null,
            services,
            null,
            new SingYaml.Agent(
                "claude-code", true, "sing/", true, null, null, null, null, null, null, null),
            null,
            new SingYaml.Ssh("dev", List.of("ssh-ed25519 AAAA...")));
    var state = new ContainerState.Running("10.0.0.42");
    var out = new ByteArrayOutputStream();
    Banner.printProjectConfig(config, state, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("acme-health"));
    assertTrue(output.contains("Acme Health Platform"));
    assertTrue(output.contains("4 cores"));
    assertTrue(output.contains("12GB"));
    assertTrue(output.contains("150GB"));
    assertTrue(output.contains("Running"));
    assertTrue(output.contains("10.0.0.42"));
    assertTrue(output.contains("25"));
    assertTrue(output.contains("22"));
    assertTrue(output.contains("postgres (5432)"));
    assertTrue(output.contains("meilisearch (7700)"));
    assertTrue(output.contains("claude-code"));
    assertTrue(output.contains("dev"));
  }

  @Test
  void projectConfigWithStoppedContainer() {
    var config =
        new SingYaml(
            "test",
            null,
            new SingYaml.Resources(2, "4GB", "50GB"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    var state = new ContainerState.Stopped();
    var out = new ByteArrayOutputStream();
    Banner.printProjectConfig(config, state, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("Stopped"));
  }

  @Test
  void projectConfigMinimalConfig() {
    var config =
        new SingYaml(
            "minimal",
            null,
            new SingYaml.Resources(2, "4GB", "50GB"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    var state = new ContainerState.NotCreated();
    var out = new ByteArrayOutputStream();
    Banner.printProjectConfig(config, state, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("minimal"));
    assertTrue(output.contains("Not created"));
    assertFalse(output.contains("JDK:"));
    assertFalse(output.contains("Agent:"));
    assertFalse(output.contains("SSH user:"));
  }

  @Test
  void projectConfigBordersAlign() {
    var config =
        new SingYaml(
            "acme",
            "Desc",
            new SingYaml.Resources(2, "4GB", "50GB"),
            "ubuntu/24.04",
            null,
            new SingYaml.Runtimes(25, null, null),
            null,
            null,
            null,
            null,
            new SingYaml.Agent(
                "claude-code", false, null, true, null, null, null, null, null, null, null),
            null,
            new SingYaml.Ssh("dev", null));
    var state = new ContainerState.Running("10.0.0.1");
    var out = new ByteArrayOutputStream();
    Banner.printProjectConfig(config, state, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    for (var line : output.split("\n")) {
      var pipeCount = line.chars().filter(c -> c == '\u2502').count();
      if (pipeCount > 0) {
        assertEquals(2, pipeCount, "Misaligned borders in: " + line);
      }
    }
  }

  @Test
  void overcommitWarningShowsCpuAndMemory() {
    var status = new ResourceChecker.OvercommitStatus(12, 6, 49152, 32768);
    var out = new ByteArrayOutputStream();
    Banner.printOvercommitWarning(status, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("Overcommit warning"));
    assertTrue(output.contains("CPU 12/6 threads"));
    assertTrue(output.contains("Memory 48GB/32GB"));
    assertTrue(output.contains("sing down"));
  }

  @Test
  void overcommitWarningCpuOnly() {
    var status = new ResourceChecker.OvercommitStatus(12, 6, 16384, 32768);
    var out = new ByteArrayOutputStream();
    Banner.printOvercommitWarning(status, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("CPU 12/6"));
    assertFalse(output.contains("Memory"));
  }

  @Test
  void overcommitWarningNotOvercommittedPrintsNothing() {
    var status = new ResourceChecker.OvercommitStatus(4, 12, 8192, 32768);
    var out = new ByteArrayOutputStream();
    Banner.printOvercommitWarning(status, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.isEmpty());
  }

  @Test
  void alsoRunningShowsProjects() {
    var others =
        List.of(
            new ContainerManager.ContainerInfo(
                "client-b",
                new ContainerState.Running("10.0.0.2"),
                new ContainerManager.ResourceLimits("4", "8GB")));
    var out = new ByteArrayOutputStream();
    Banner.printAlsoRunning(others, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("Also running"));
    assertTrue(output.contains("client-b"));
    assertTrue(output.contains("4 CPU"));
    assertTrue(output.contains("8GB"));
  }

  @Test
  void alsoRunningEmptyPrintsNothing() {
    var out = new ByteArrayOutputStream();
    Banner.printAlsoRunning(List.of(), new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.isEmpty());
  }

  @Test
  void projectTableShowsResourceColumns() {
    var containers =
        List.of(
            new ContainerManager.ContainerInfo(
                "acme",
                new ContainerState.Running("10.0.0.42"),
                new ContainerManager.ResourceLimits("4", "12GB")),
            new ContainerManager.ContainerInfo(
                "fintech",
                new ContainerState.Stopped(),
                new ContainerManager.ResourceLimits("2", "8GB")));
    var out = new ByteArrayOutputStream();
    Banner.printProjectTable(containers, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("CPU"));
    assertTrue(output.contains("MEM"));
    assertTrue(output.contains("12GB"));
    assertTrue(output.contains("8GB"));
  }

  @Test
  void projectTableAlignsBordersWithLongNames() {
    var containers =
        List.of(
            new ContainerManager.ContainerInfo(
                "singular-website-blog",
                new ContainerState.Running("10.145.172.48"),
                new ContainerManager.ResourceLimits("2", "4GB")),
            new ContainerManager.ContainerInfo(
                "manatee",
                new ContainerState.Running("10.145.172.50"),
                new ContainerManager.ResourceLimits("4", "16GB")));
    var out = new ByteArrayOutputStream();
    Banner.printProjectTable(containers, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    for (var line : output.split("\n")) {
      var pipeCount = line.chars().filter(c -> c == '\u2502').count();
      if (pipeCount > 0) {
        assertEquals(2, pipeCount, "Misaligned borders in: " + line);
      }
    }
    assertTrue(output.contains("singular-website-blog"));
    assertTrue(output.contains("manatee"));
  }

  @Test
  void agentReportShowsAllSections() {
    var report =
        new AgentReporter.Report(
            "acme-health",
            "Completed",
            "2026-03-02T01:00:00Z",
            "2026-03-02T04:42:00Z",
            "3h 42m",
            "sing/20260302-010000",
            List.of(
                new Spec("auth", "Implement JWT", "done", null, List.of(), null),
                new Spec("tests", "Write tests", "done", null, List.of("auth"), null),
                new Spec("docs", "Update docs", "pending", null, List.of(), null)),
            18,
            47,
            false,
            null,
            null,
            false,
            null);
    var out = new ByteArrayOutputStream();
    Banner.printAgentReport("acme-health", report, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("Agent Report"));
    assertTrue(output.contains("Completed"));
    assertTrue(output.contains("3h 42m"));
    assertTrue(output.contains("sing/20260302-010000"));
    assertTrue(output.contains("auth"));
    assertTrue(output.contains("Implement JWT"));
    assertTrue(output.contains("tests"));
    assertTrue(output.contains("docs"));
    assertTrue(output.contains("Update docs"));
    assertTrue(output.contains("18 commits"));
    assertTrue(output.contains("Not triggered"));
    assertTrue(output.contains("sing agent log"));
    assertTrue(output.contains("sing dispatch"));
  }

  @Test
  void agentReportGuardrailTriggered() {
    var report =
        new AgentReporter.Report(
            "acme",
            "Killed by guardrail",
            "2026-03-02T01:00:00Z",
            "2026-03-02T05:00:00Z",
            "4h 0m",
            "sing/snap",
            List.of(),
            45,
            240,
            true,
            "max_duration",
            "snapshot-and-stop",
            false,
            null);
    var out = new ByteArrayOutputStream();
    Banner.printAgentReport("acme", report, new PrintStream(out), Ansi.OFF);
    var output = out.toString(StandardCharsets.UTF_8);

    assertTrue(output.contains("Killed by guardrail"));
    assertTrue(output.contains("Triggered"));
    assertTrue(output.contains("max_duration"));
    assertTrue(output.contains("snapshot-and-stop"));
  }

  private static String renderBanner(HostDetector.HostInfo info) {
    var out = new ByteArrayOutputStream();
    Banner.printHostDetection(info, new PrintStream(out), Ansi.OFF);
    return out.toString(StandardCharsets.UTF_8);
  }
}
