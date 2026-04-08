/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

/**
 * Detects the current platform (OS and architecture) for binary downloads and validation. Used by
 * the auto-upgrader and release fetcher to select the correct binary artifact.
 */
public final class PlatformDetector {

  private static final byte[] ELF_MAGIC = {0x7f, 'E', 'L', 'F'};
  private static final byte[] MACHO_64_MAGIC = {(byte) 0xcf, (byte) 0xfa, (byte) 0xed, (byte) 0xfe};
  private static final byte[] MACHO_UNIVERSAL_MAGIC = {
    (byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe
  };

  private PlatformDetector() {}

  /**
   * Returns the platform suffix for binary downloads (e.g. {@code "linux-amd64"}, {@code
   * "darwin-arm64"}).
   */
  public static String platformSuffix() {
    return platformSuffix(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
  }

  /** Package-private overload for testability. */
  static String platformSuffix(String osName, String osArch) {
    var os = osName.toLowerCase();
    if (os.contains("mac") || os.contains("darwin")) {
      var arch = "aarch64".equals(osArch) || "arm64".equals(osArch) ? "arm64" : "amd64";
      return "darwin-" + arch;
    }
    return "linux-amd64";
  }

  /**
   * Validates that the given binary bytes match the expected executable format for the current
   * platform. ELF on Linux, Mach-O on macOS.
   */
  public static boolean isValidBinary(byte[] binary) {
    return isValidBinary(binary, System.getProperty("os.name", ""));
  }

  /** Package-private overload for testability. */
  static boolean isValidBinary(byte[] binary, String osName) {
    if (binary == null || binary.length < 4) {
      return false;
    }
    var os = osName.toLowerCase();
    if (os.contains("mac") || os.contains("darwin")) {
      return matchesMagic(binary, MACHO_64_MAGIC) || matchesMagic(binary, MACHO_UNIVERSAL_MAGIC);
    }
    return matchesMagic(binary, ELF_MAGIC);
  }

  private static boolean matchesMagic(byte[] binary, byte[] magic) {
    for (var i = 0; i < magic.length; i++) {
      if (binary[i] != magic[i]) {
        return false;
      }
    }
    return true;
  }
}
