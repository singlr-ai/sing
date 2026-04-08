/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Centralized path constants for all {@code sing} state files. All state lives under {@code
 * ~/.sing/} — project descriptors, provisioning state, host config, and client config. This makes
 * every command work from any directory by project name alone.
 */
public final class SingPaths {

  private SingPaths() {}

  private static final Path SING_DIR = Path.of(System.getProperty("user.home"), ".sing");
  private static final Path LEGACY_DIR = Path.of("/etc/sing");
  private static final Path PROJECTS_DIR = SING_DIR.resolve("projects");

  /** Returns the base sing directory: {@code ~/.sing}. */
  public static Path singDir() {
    return SING_DIR;
  }

  /** Returns the projects base directory: {@code ~/.sing/projects}. */
  public static Path projectsDir() {
    return PROJECTS_DIR;
  }

  /** Returns the project-specific directory: {@code ~/.sing/projects/<name>}. */
  public static Path projectDir(String name) {
    return PROJECTS_DIR.resolve(name);
  }

  /** Returns the provision state file: {@code ~/.sing/projects/<name>/provision-state.yaml}. */
  public static Path provisionState(String name) {
    return projectDir(name).resolve("provision-state.yaml");
  }

  /** Returns the update check cache file: {@code ~/.sing/update-check.yaml}. */
  public static Path updateCheckFile() {
    return SING_DIR.resolve("update-check.yaml");
  }

  /**
   * Returns the host config file path. Prefers {@code ~/.sing/host.yaml} (new location). Falls back
   * to {@code /etc/sing/host.yaml} (legacy) if it exists and the new location does not.
   */
  public static Path hostConfigPath() {
    var newPath = SING_DIR.resolve("host.yaml");
    if (Files.exists(newPath)) {
      return newPath;
    }
    var legacyPath = LEGACY_DIR.resolve("host.yaml");
    if (Files.exists(legacyPath)) {
      return legacyPath;
    }
    return newPath;
  }

  /**
   * Returns the preferred host config path for writing: {@code ~/.sing/host.yaml}. Always returns
   * the new location — used by {@code host init} to write new config.
   */
  public static Path hostConfigWritePath() {
    return SING_DIR.resolve("host.yaml");
  }

  /**
   * Returns the legacy host config path: {@code /etc/sing/host.yaml}. Used for backward
   * compatibility detection.
   */
  public static Path legacyHostConfigPath() {
    return LEGACY_DIR.resolve("host.yaml");
  }

  /** Returns the client config file path: {@code ~/.sing/config.yaml}. */
  public static Path clientConfigPath() {
    return SING_DIR.resolve("config.yaml");
  }

  /**
   * Resolves the sing.yaml path for a project. Checks in order:
   *
   * <ol>
   *   <li>{@code ~/.sing/projects/<name>/sing.yaml} (canonical location)
   *   <li>The explicit {@code file} path (from {@code -f} flag)
   *   <li>{@code <name>/sing.yaml} in the current directory
   * </ol>
   *
   * Returns the first path that exists, or the explicit file path for the error message.
   */
  public static Path resolveSingYaml(String name, String file) {
    if (name != null) {
      var canonical = projectDir(name).resolve("sing.yaml");
      if (Files.exists(canonical)) {
        return canonical;
      }
    }
    var path = Path.of(file);
    if (Files.exists(path)) {
      return path;
    }
    if (name != null) {
      var namedPath = Path.of(name, "sing.yaml");
      if (Files.exists(namedPath)) {
        return namedPath;
      }
    }
    return path;
  }

  /**
   * Expands a leading {@code ~} to the current user's home directory. Returns the path unchanged if
   * it does not start with {@code ~/}.
   */
  public static String expandHome(String path) {
    if (path != null && path.startsWith("~/")) {
      return System.getProperty("user.home") + path.substring(1);
    }
    return path;
  }

  /**
   * Returns the path to the running binary. Uses {@code /proc/self/exe} on Linux, falls back to
   * {@code /usr/local/bin/sing}.
   */
  public static Path binaryPath() {
    var procSelf = Path.of("/proc/self/exe");
    try {
      if (Files.exists(procSelf)) {
        return procSelf.toRealPath();
      }
    } catch (IOException ignored) {
    }
    return Path.of("/usr/local/bin/sing");
  }
}
