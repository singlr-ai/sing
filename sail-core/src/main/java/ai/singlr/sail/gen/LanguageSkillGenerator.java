/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.gen;

import ai.singlr.sail.config.SailYaml;
import ai.singlr.sail.engine.AgentCli;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates language-standard skills from a project's configured {@link SailYaml.Runtimes
 * runtimes}. The Java and TypeScript coding standards used to be inlined into the always-loaded
 * agent context body; they now ship as on-demand skills the agent loads by description only when it
 * is actually writing that language. A project receives the {@code java} skill only when a JDK is
 * configured and the {@code typescript} skill only when Node is — a non-Java project never carries
 * Java standards anywhere.
 *
 * <p>Skills are sail-owned and regenerated every run, as real {@code SKILL.md} files for both
 * agents ({@code .claude/skills/<name>/SKILL.md} and {@code .agents/skills/<name>/SKILL.md}). Pure
 * utility — no I/O, no shell execution.
 */
public final class LanguageSkillGenerator {

  private LanguageSkillGenerator() {}

  /**
   * Generates the language-standard skills the configured runtimes call for.
   *
   * @param agent the target agent type, which fixes the skills directory layout
   * @param runtimes the project's runtimes; when {@code null} no language skills are generated
   * @param basePath the workspace base path (e.g. {@code /home/dev/workspace/})
   * @return the {@code java} and/or {@code typescript} skill files, or empty when neither applies
   */
  public static List<GeneratedFile> generateFiles(
      AgentCli agent, SailYaml.Runtimes runtimes, String basePath) {
    if (runtimes == null) {
      return List.of();
    }
    var files = new ArrayList<GeneratedFile>();
    if (runtimes.jdk() > 0) {
      files.add(
          new GeneratedFile(skillFile(agent, basePath, "java"), javaSkill(runtimes.jdk()), false));
    }
    if (runtimes.node() != null) {
      files.add(
          new GeneratedFile(skillFile(agent, basePath, "typescript"), typescriptSkill(), false));
    }
    return List.copyOf(files);
  }

  private static String skillFile(AgentCli agent, String basePath, String name) {
    return basePath + agent.skillsDir() + name + "/SKILL.md";
  }

  private static String javaSkill(int jdk) {
    return """
        ---
        name: java
        description: >
          Java coding standards for this project (JDK %d). Apply whenever writing or reviewing
          Java here: records for value types, var for locals, pattern matching, virtual threads
          for I/O, sealed types, text blocks, immutable collections, try-with-resources, streams.
        ---

        # Java (JDK %d) Standards

        Apply these standards to all Java you write or review in this project.

        """
            .formatted(jdk, jdk)
        + javaConventions();
  }

  private static String typescriptSkill() {
    return """
        ---
        name: typescript
        description: >
          Node.js / TypeScript coding standards for this project. Apply whenever writing or
          reviewing TypeScript or JavaScript here: functional components with hooks, async/await,
          ESM imports, const over let, destructuring, strict null checks.
        ---

        # Node.js / TypeScript Standards

        Apply these standards to all TypeScript and JavaScript you write or review in this project.

        """
        + nodeConventions();
  }

  /** Java coding conventions for modern JDK projects. */
  static String javaConventions() {
    return """
        - Records for all value types: DTOs, results, events, config objects.
        - `var` for all method-scoped local variables with immediate initialization.
        - Pattern matching: `instanceof` patterns, switch expressions with patterns.
        - Virtual threads for all I/O operations, never platform threads.
        - Sealed interfaces for domain types and algebraic data modeling.
        - Text blocks (`\"\"\"`) for any multi-line string.
        - Immutable collections: `List.of()`, `Set.of()`, `Map.of()` for constants and returns.
        - Try-with-resources for anything AutoCloseable.
        - No old APIs: no Date/Calendar (use java.time), no Vector/Hashtable, no StringBuffer.
        - Streams for transforms and aggregations; imperative loops for side effects.
        """;
  }

  /** Node.js and TypeScript coding conventions. */
  static String nodeConventions() {
    return """
        - Functional components with hooks (no class components).
        - TypeScript preferred over plain JavaScript.
        - async/await for all asynchronous operations (no raw Promise chains).
        - ESM imports (`import`/`export`), not CommonJS `require()`.
        - `const` by default, `let` when reassignment is needed, never `var`.
        - Destructuring for function parameters and object access.
        - Strict null checks enabled. Handle undefined/null explicitly.
        """;
  }
}
