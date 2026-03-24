/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Centralized path constants for all {@code sing} state files on the host. */
public final class SingPaths {

  private SingPaths() {}

  private static final Path SING_DIR = Path.of("/etc/sing");
  private static final Path PROJECTS_DIR = SING_DIR.resolve("projects");

  /** Returns the projects base directory: {@code /etc/sing/projects}. */
  public static Path projectsDir() {
    return PROJECTS_DIR;
  }

  /** Returns the project-specific directory: {@code /etc/sing/projects/<name>}. */
  public static Path projectDir(String name) {
    return PROJECTS_DIR.resolve(name);
  }

  /** Returns the provision state file: {@code /etc/sing/projects/<name>/provision-state.yaml}. */
  public static Path provisionState(String name) {
    return projectDir(name).resolve("provision-state.yaml");
  }

  /** Returns the update check cache file: {@code /etc/sing/update-check.yaml}. */
  public static Path updateCheckFile() {
    return SING_DIR.resolve("update-check.yaml");
  }

  /**
   * Resolves the sing.yaml path for a project. Tries the given {@code file} path first; if it
   * doesn't exist, falls back to {@code <name>/sing.yaml}. Returns the first path that exists, or
   * the original {@code file} path if neither exists (for the error message).
   */
  public static Path resolveSingYaml(String name, String file) {
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
