/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import ai.singlr.sing.SingVersion;
import ai.singlr.sing.config.YamlUtil;
import ai.singlr.sing.engine.Banner;
import ai.singlr.sing.engine.ReleaseFetcher;
import ai.singlr.sing.engine.SemVer;
import ai.singlr.sing.engine.SingPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
    name = "upgrade",
    description = "Upgrade sing to the latest version.",
    mixinStandardHelpOptions = true)
public final class UpgradeCommand implements Runnable {

  @Option(names = "--check", description = "Check for updates without installing.")
  private boolean checkOnly;

  @Option(names = "--target", description = "Install a specific version (e.g. 1.7.0).")
  private String targetVersion;

  @Option(names = "--dry-run", description = "Print actions instead of executing them.")
  private boolean dryRun;

  @Option(names = "--json", description = "Output in JSON format.")
  private boolean json;

  @Spec private CommandSpec spec;

  @Override
  public void run() {
    CliCommand.run(spec, this::execute);
  }

  private void execute() throws Exception {
    var currentVersion = SingVersion.version();
    if ("dev".equals(currentVersion)) {
      throw new IllegalStateException(
          "Cannot upgrade a development build. Install a release version first.");
    }
    var current = SemVer.parse(currentVersion);

    String latestVersionStr;
    if (targetVersion != null) {
      latestVersionStr = targetVersion.startsWith("v") ? targetVersion.substring(1) : targetVersion;
    } else {
      latestVersionStr = ReleaseFetcher.fetchLatestVersion();
    }
    var latest = SemVer.parse(latestVersionStr);
    var versionTag = "v" + latest;

    if (checkOnly) {
      printCheckResult(currentVersion, latestVersionStr, current.compareTo(latest));
      return;
    }

    if (current.compareTo(latest) >= 0 && targetVersion == null) {
      if (json) {
        printJsonResult(currentVersion, latestVersionStr, "up_to_date", null);
      } else {
        System.out.println(
            Ansi.AUTO.string(
                "  @|green \u2713|@ Already up to date (sing " + currentVersion + ")"));
      }
      return;
    }

    var binaryPath = SingPaths.binaryPath();
    var installDir = binaryPath.getParent();
    if (!dryRun && installDir != null && !Files.isWritable(installDir)) {
      if (!ConsoleHelper.isRoot()) {
        if (!json) {
          System.out.println(
              Ansi.AUTO.string("  @|faint Installing to " + installDir + " (requires sudo)...|@"));
        }
        reExecWithSudo();
        return;
      }
    }

    if (!json) {
      Banner.printBranding(System.out, Ansi.AUTO);
      System.out.println(
          Ansi.AUTO.string(
              "  @|bold Upgrading:|@ " + currentVersion + " \u2192 " + latestVersionStr));
      System.out.println();
    }

    if (!json) {
      System.out.println(
          Banner.stepLine(1, 3, "Downloading sing " + versionTag + "...", Ansi.AUTO));
    }
    byte[] binary;
    if (dryRun) {
      System.out.println(
          "[dry-run] Download " + ReleaseFetcher.buildDownloadUrl(versionTag, "sing"));
      binary = new byte[0];
    } else {
      binary =
          targetVersion != null
              ? ReleaseFetcher.downloadBinary(versionTag)
              : ReleaseFetcher.downloadLatestBinary();
      if (!json) {
        System.out.println(
            Banner.stepDoneLine(
                1, 3, "Downloaded (" + (binary.length / 1024 / 1024) + " MB)", Ansi.AUTO));
      }
    }

    if (!json) {
      System.out.println(Banner.stepLine(2, 3, "Verifying checksum...", Ansi.AUTO));
    }
    if (dryRun) {
      System.out.println("[dry-run] Verify SHA-256 checksum");
    } else {
      var expectedChecksum =
          targetVersion != null
              ? ReleaseFetcher.fetchChecksum(versionTag)
              : ReleaseFetcher.fetchLatestChecksum();
      var digest = MessageDigest.getInstance("SHA-256");
      var actualChecksum = HexFormat.of().formatHex(digest.digest(binary));
      if (!actualChecksum.equalsIgnoreCase(expectedChecksum)) {
        throw new IOException(
            "Checksum mismatch.\n  Expected: "
                + expectedChecksum
                + "\n  Actual:   "
                + actualChecksum
                + "\n  The download may be corrupted. Try again.");
      }
      if (!json) {
        System.out.println(Banner.stepDoneLine(2, 3, "Checksum verified", Ansi.AUTO));
      }
    }

    if (!dryRun && binary.length > 4) {
      if (binary[0] != 0x7f || binary[1] != 'E' || binary[2] != 'L' || binary[3] != 'F') {
        throw new IOException("Downloaded file is not a valid ELF binary.");
      }
    }

    if (!json) {
      System.out.println(Banner.stepLine(3, 3, "Installing to " + binaryPath + "...", Ansi.AUTO));
    }
    if (dryRun) {
      System.out.println("[dry-run] Write new binary to " + binaryPath);
      System.out.println("[dry-run] chmod +x " + binaryPath);
    } else {
      var tmpPath = binaryPath.resolveSibling("sing.tmp");
      Files.write(tmpPath, binary);
      Files.setPosixFilePermissions(tmpPath, Files.getPosixFilePermissions(binaryPath));
      Files.move(
          tmpPath, binaryPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      if (!json) {
        System.out.println(Banner.stepDoneLine(3, 3, "Installed", Ansi.AUTO));
      }
    }

    if (json) {
      printJsonResult(currentVersion, latestVersionStr, "upgraded", binaryPath.toString());
    } else {
      System.out.println();
      System.out.println(
          Ansi.AUTO.string("  @|bold,green \u2713 Upgraded to sing " + latestVersionStr + "|@"));
    }
  }

  private void printCheckResult(String current, String latest, int comparison) {
    if (json) {
      var map = new LinkedHashMap<String, Object>();
      map.put("current", current);
      map.put("latest", latest);
      map.put("update_available", comparison < 0);
      System.out.println(YamlUtil.dumpJson(map));
    } else {
      if (comparison < 0) {
        System.out.println(
            Ansi.AUTO.string("  @|bold Update available:|@ " + current + " \u2192 " + latest));
        System.out.println(Ansi.AUTO.string("  @|faint Run 'sing upgrade' to install.|@"));
      } else {
        System.out.println(
            Ansi.AUTO.string("  @|green \u2713|@ Already up to date (sing " + current + ")"));
      }
    }
  }

  private void printJsonResult(String from, String to, String status, String path) {
    var map = new LinkedHashMap<String, Object>();
    map.put("status", status);
    map.put("from", from);
    map.put("to", to);
    if (path != null) {
      map.put("path", path);
    }
    System.out.println(YamlUtil.dumpJson(map));
  }

  /** Re-executes the current command with sudo, inheriting stdin/stdout/stderr. */
  private void reExecWithSudo() throws IOException, InterruptedException {
    var args = new java.util.ArrayList<String>();
    args.add("sudo");
    args.add(SingPaths.binaryPath().toString());
    args.add("upgrade");
    if (targetVersion != null) {
      args.add("--target");
      args.add(targetVersion);
    }
    if (json) {
      args.add("--json");
    }
    var process = new ProcessBuilder(args).inheritIO().start();
    var exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new IllegalStateException("sudo sing upgrade failed (exit code " + exitCode + ")");
    }
  }
}
