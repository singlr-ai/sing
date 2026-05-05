/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import ai.singlr.sing.SingVersion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import picocli.CommandLine.Help.Ansi;

/**
 * Silent auto-upgrader that runs before every CLI command. When a newer version is available,
 * downloads the binary, verifies its checksum and ELF header, replaces the current binary, and
 * re-execs the original command with the new version.
 *
 * <p>Rate-limited by {@link UpdateChecker}'s 24-hour cache — no network call when cache is fresh.
 * On any failure, silently falls back to the current binary. Never disrupts the actual command.
 *
 * <p>Skips for: dev builds, {@code SAIL_NO_UPDATE_CHECK=1}, {@code SAIL_AUTO_UPGRADED=1}, legacy
 * {@code SING_NO_UPDATE_CHECK=1}, legacy {@code SING_AUTO_UPGRADED=1}, {@code upgrade} subcommand
 * (handles itself), {@code --version}/{@code --help}.
 */
public final class AutoUpgrader {

  private static final Set<String> SKIP_ARGS = Set.of("upgrade", "--version", "-V", "--help", "-h");

  private AutoUpgrader() {}

  /**
   * Checks for a newer version and auto-upgrades if available. Call early in {@code main()} before
   * command execution. If an upgrade succeeds, this method re-execs the original command with the
   * new binary and calls {@link System#exit} — it never returns in that case.
   *
   * @param args the original command-line arguments
   */
  public static void upgradeIfAvailable(String[] args) {
    if (shouldSkip(args)) {
      return;
    }
    try {
      doUpgrade(args);
    } catch (Exception ignored) {
    }
  }

  /** Package-private for testing. */
  static boolean shouldSkip(String[] args) {
    if ("dev".equals(SingVersion.version())) {
      return true;
    }
    if ("1".equals(System.getenv("SAIL_NO_UPDATE_CHECK"))
        || "1".equals(System.getenv("SING_NO_UPDATE_CHECK"))) {
      return true;
    }
    if ("1".equals(System.getenv("SAIL_AUTO_UPGRADED"))
        || "1".equals(System.getenv("SING_AUTO_UPGRADED"))) {
      return true;
    }
    for (var arg : args) {
      if (SKIP_ARGS.contains(arg)) {
        return true;
      }
    }
    return false;
  }

  private static void doUpgrade(String[] args) throws Exception {
    var cacheFile = SingPaths.updateCheckFile();
    var latestVersionStr = UpdateChecker.doCheck(cacheFile);
    if (latestVersionStr == null) {
      return;
    }

    var current = SemVer.parse(SingVersion.version());
    var latest = SemVer.parse(latestVersionStr);
    if (current.compareTo(latest) >= 0) {
      return;
    }

    var versionTag = "v" + latest;
    var binaryPath = SingPaths.binaryPath();
    var installDir = binaryPath.getParent();

    System.err.println(
        Ansi.AUTO.string(
            "  @|faint Updating sail "
                + SingVersion.version()
                + " \u2192 "
                + latestVersionStr
                + "...|@"));

    var binary = ReleaseFetcher.downloadBinary(versionTag);

    var expectedChecksum = ReleaseFetcher.fetchChecksum(versionTag);
    var digest = MessageDigest.getInstance("SHA-256");
    var actualChecksum = HexFormat.of().formatHex(digest.digest(binary));
    if (!actualChecksum.equalsIgnoreCase(expectedChecksum)) {
      return;
    }

    if (!PlatformDetector.isValidBinary(binary)) {
      return;
    }

    if (installDir != null && Files.isWritable(installDir)) {
      installDirect(binary, binaryPath);
    } else {
      installWithSudo(binary, binaryPath);
    }

    System.err.println(
        Ansi.AUTO.string(
            "  @|bold,green \u2713|@ @|faint Updated to sail " + latestVersionStr + "|@\n"));

    reExec(binaryPath, args);
  }

  private static void installDirect(byte[] binary, Path binaryPath) throws IOException {
    var tmpPath = binaryPath.resolveSibling("sing.tmp");
    Files.write(tmpPath, binary);
    if (Files.exists(binaryPath)) {
      Files.setPosixFilePermissions(tmpPath, Files.getPosixFilePermissions(binaryPath));
    } else {
      Files.setPosixFilePermissions(
          tmpPath,
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE,
              PosixFilePermission.GROUP_READ,
              PosixFilePermission.GROUP_EXECUTE,
              PosixFilePermission.OTHERS_READ,
              PosixFilePermission.OTHERS_EXECUTE));
    }
    Files.move(
        tmpPath, binaryPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  private static void installWithSudo(byte[] binary, Path binaryPath) throws Exception {
    var tmpFile = Files.createTempFile("sing-upgrade-", ".tmp");
    try {
      Files.write(tmpFile, binary);
      var process =
          new ProcessBuilder(
                  "sudo", "install", "-m", "755", tmpFile.toString(), binaryPath.toString())
              .inheritIO()
              .start();
      var exitCode = process.waitFor();
      if (exitCode != 0) {
        throw new IOException("sudo install failed (exit " + exitCode + ")");
      }
    } finally {
      Files.deleteIfExists(tmpFile);
    }
  }

  private static void reExec(Path binaryPath, String[] args) throws Exception {
    var cmd = new ArrayList<String>();
    cmd.add(binaryPath.toString());
    cmd.addAll(List.of(args));
    var pb = new ProcessBuilder(cmd);
    pb.environment().put("SING_AUTO_UPGRADED", "1");
    pb.environment().put("SAIL_AUTO_UPGRADED", "1");
    pb.inheritIO();
    var exitCode = pb.start().waitFor();
    System.exit(exitCode);
  }
}
