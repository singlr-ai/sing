/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

/** Simple semantic version record with parsing and comparison. */
public record SemVer(int major, int minor, int patch) implements Comparable<SemVer> {

  /**
   * Parses a version string like {@code "1.7.0"}, {@code "v1.7.0"}, or {@code "1.7"}. The leading
   * {@code v} is stripped if present. A missing patch component defaults to 0.
   */
  public static SemVer parse(String version) {
    var v = version.startsWith("v") ? version.substring(1) : version;
    var parts = v.split("\\.");
    if (parts.length < 2) {
      throw new IllegalArgumentException("Invalid version: " + version);
    }
    var major = Integer.parseInt(parts[0]);
    var minor = Integer.parseInt(parts[1]);
    var patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
    return new SemVer(major, minor, patch);
  }

  @Override
  public int compareTo(SemVer other) {
    var c = Integer.compare(major, other.major);
    if (c != 0) {
      return c;
    }
    c = Integer.compare(minor, other.minor);
    if (c != 0) {
      return c;
    }
    return Integer.compare(patch, other.patch);
  }

  @Override
  public String toString() {
    return major + "." + minor + "." + patch;
  }
}
