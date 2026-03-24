/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.gen;

/**
 * A file to be pushed into the project container. Used by generators that produce files without
 * performing I/O themselves — callers handle the actual push.
 *
 * @param remotePath absolute path inside the container
 * @param content file content
 * @param executable whether the file should be marked executable
 * @param skipIfExists if true, do not overwrite when a file with the same name (case-insensitive)
 *     already exists at the target path — the repo's own version takes precedence
 */
public record GeneratedFile(
    String remotePath, String content, boolean executable, boolean skipIfExists) {

  public GeneratedFile(String remotePath, String content, boolean executable) {
    this(remotePath, content, executable, false);
  }
}
