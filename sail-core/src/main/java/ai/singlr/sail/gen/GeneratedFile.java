/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.gen;

/**
 * A file to be pushed into the project container. Used by generators that produce files without
 * performing I/O themselves — callers handle the actual push. Every generated file is sail-owned
 * and lives in sail's home namespace ({@code ~/.claude}, {@code ~/.codex}, {@code
 * ~/.agents/skills}, {@code ~/.sail}); the installer overwrites it on every run. Sail never
 * generates files in the engineer's workspace, so there is no ownership distinction to carry.
 *
 * @param remotePath absolute path inside the container
 * @param content file content
 * @param executable whether the file should be marked executable
 */
public record GeneratedFile(String remotePath, String content, boolean executable) {}
