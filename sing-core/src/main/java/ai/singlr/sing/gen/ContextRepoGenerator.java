/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.gen;

import java.util.List;

/**
 * Generates the context repository scaffold — a git-backed directory structure inside each project
 * that persists agent learnings across sessions. Pure utility — no I/O, no shell execution.
 *
 * <p>The context repo uses files as the universal memory primitive (readable by both agents and
 * humans) and git as the versioning/coordination layer. Agents read from and write to these files
 * using standard file tools. The structure supports progressive disclosure: {@code system/} is
 * always loaded, other directories are consulted on demand.
 */
public final class ContextRepoGenerator {

  private ContextRepoGenerator() {}

  /**
   * Generates the context repository scaffold files for a project. Returns the files that should be
   * pushed into the container at {@code basePath/.context/}.
   *
   * @param basePath the workspace base path (e.g. {@code /home/dev/workspace/})
   * @param projectName the project name for the README
   */
  public static List<GeneratedFile> generateFiles(String basePath, String projectName) {
    var contextBase = basePath + ".context/";

    return List.of(
        new GeneratedFile(
            contextBase + "system/README.md", generateSystemReadme(projectName), false),
        new GeneratedFile(contextBase + "patterns/README.md", generatePatternsReadme(), false),
        new GeneratedFile(contextBase + "failures/README.md", generateFailuresReadme(), false),
        new GeneratedFile(contextBase + ".gitignore", "", false));
  }

  /**
   * Generates CLAUDE.md instructions that tell the agent how to use the context repository. This
   * content is appended to the generated CLAUDE.md by the caller.
   */
  public static String generateContextInstructions() {
    return """

        ## Context Repository

        A `.context/` directory exists in your workspace. It persists across sessions — use it to
        build institutional memory about this project.

        ### Structure
        - `.context/system/` — Always review at session start. Contains project overview and key decisions.
        - `.context/patterns/` — Discovered patterns, conventions, and architectural insights.
        - `.context/failures/` — What went wrong and how it was fixed. Add an entry every time you
          encounter a non-obvious failure so future sessions avoid repeating the same mistake.

        ### Rules
        - **Read `system/README.md` at the start of every session** before doing any work.
        - When you discover something non-obvious about the codebase, write it to the appropriate
          directory (patterns/ for conventions, failures/ for debugging insights).
        - Keep entries concise — one file per topic, descriptive filename, markdown format.
        - Commit context changes alongside your code changes.
        """;
  }

  private static String generateSystemReadme(String projectName) {
    return """
        # %s — Project Context

        > This file is read by the agent at the start of every session.
        > Keep it concise and up to date.

        ## Overview
        <!-- Brief description of the project, its purpose, and key stakeholders. -->

        ## Key Decisions
        <!-- Architecture decisions, technology choices, and their rationale. -->

        ## Current State
        <!-- What's in progress, what's blocked, what's next. -->
        """
        .formatted(projectName);
  }

  private static String generatePatternsReadme() {
    return """
        # Patterns

        > Discovered patterns, conventions, and architectural insights.
        > One file per topic. Use descriptive filenames.

        Files in this directory document recurring patterns in the codebase
        that are not obvious from reading the code alone.
        """;
  }

  private static String generateFailuresReadme() {
    return """
        # Failures

        > What went wrong and how it was fixed.
        > Add an entry every time you encounter a non-obvious failure.

        Each entry prevents a class of future failures. Format:
        - **Symptom**: What happened
        - **Root cause**: Why it happened
        - **Fix**: What solved it
        """;
  }
}
