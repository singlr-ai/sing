/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.gen;

import ai.singlr.sing.config.Methodology;
import ai.singlr.sing.engine.AgentCli;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates methodology-aware skills, hooks, and CLAUDE.md instructions from a {@link Methodology}
 * configuration. Pure utility — no I/O, no shell execution.
 *
 * <p>Methodology types:
 *
 * <ul>
 *   <li>{@code spec-driven} — write a spec before coding, implement against the spec
 *   <li>{@code tdd} — write failing tests first, then implement to pass
 *   <li>{@code free-form} — no structural constraints (default)
 * </ul>
 */
public final class MethodologyGenerator {

  private MethodologyGenerator() {}

  /**
   * Generates methodology-related files (skills, hook scripts) for the given agent and methodology
   * configuration.
   *
   * @param agent the target agent type
   * @param methodology the methodology configuration (may be null)
   * @param basePath the workspace base path (e.g. {@code /home/dev/workspace/})
   */
  public static List<GeneratedFile> generateFiles(
      AgentCli agent, Methodology methodology, String basePath) {
    if (methodology == null) {
      return List.of();
    }

    var files = new ArrayList<GeneratedFile>();

    if ("spec-driven".equals(methodology.approach())) {
      var skillPath = skillPath(agent, basePath, "spec");
      if (skillPath != null) {
        files.add(new GeneratedFile(skillPath, generateSpecSkill(agent), false));
      }
    }

    if (methodology.verify() != null) {
      var skillPath = skillPath(agent, basePath, "verify");
      if (skillPath != null) {
        files.add(new GeneratedFile(skillPath, generateVerifySkill(agent, methodology), false));
      }
    }

    return List.copyOf(files);
  }

  /**
   * Generates CLAUDE.md instructions for the configured methodology. Returns empty string if no
   * methodology is configured.
   */
  public static String generateMethodologyInstructions(Methodology methodology) {
    if (methodology == null) {
      return "";
    }

    var sb = new StringBuilder();
    sb.append("\n## Methodology\n");

    if ("spec-driven".equals(methodology.approach())) {
      sb.append(
          """

          This project uses **spec-driven development**. Before writing any implementation code:
          1. Write a spec (SPEC.md or inline) describing what you will build, the approach, and edge cases
          2. Get the spec right before writing code — think through the design
          3. Implement against the spec
          4. Verify the implementation matches the spec
          Use `/spec` to create a spec for your current task.
          """);
    } else if ("tdd".equals(methodology.approach())) {
      sb.append(
          """

          This project uses **test-driven development**. For every change:
          1. Write a failing test that describes the expected behavior
          2. Run the test to confirm it fails
          3. Write the minimum code to make the test pass
          4. Refactor if needed, keeping tests green
          Never write implementation code without a failing test first.
          """);
    }

    if (methodology.verify() != null) {
      sb.append("\n### Verification\n");
      sb.append("After completing any implementation, run: `")
          .append(methodology.verify())
          .append("`\n");
      sb.append("Do not consider work done until verification passes.\n");
    }

    if (methodology.lint() != null) {
      sb.append("\n### Linting\n");
      sb.append("After editing files, run: `").append(methodology.lint()).append("`\n");
      sb.append("Fix any issues before proceeding.\n");
    }

    return sb.toString();
  }

  private static String skillPath(AgentCli agent, String basePath, String skillName) {
    return switch (agent) {
      case CLAUDE_CODE -> basePath + ".claude/skills/" + skillName + "/SKILL.md";
      case CODEX -> basePath + ".agents/skills/" + skillName + "/SKILL.md";
      case GEMINI -> basePath + ".gemini/commands/" + skillName + ".toml";
    };
  }

  private static String generateSpecSkill(AgentCli agent) {
    if (agent == AgentCli.GEMINI) {
      return """
          [command]
          description = "Write a spec before implementing"

          [command.prompt]
          text = "Before implementing, write a detailed spec covering: 1) What you will build, 2) The approach and key design decisions, 3) Edge cases and error handling, 4) Test strategy. Write the spec, then implement against it."
          """;
    }

    return """
        ---
        name: spec
        description: Write a spec before implementing a feature or fix
        ---
        Before implementing $ARGUMENTS, write a detailed spec covering:

        1. **What** — What exactly will be built or changed
        2. **Approach** — Key design decisions and why
        3. **Edge cases** — What could go wrong, boundary conditions
        4. **Test strategy** — How to verify correctness

        Write the spec as a markdown section in the relevant file or as SPEC.md.
        Once the spec is complete, implement against it.
        """;
  }

  private static String generateVerifySkill(AgentCli agent, Methodology methodology) {
    if (agent == AgentCli.GEMINI) {
      return """
          [command]
          description = "Run verification"

          [command.prompt]
          text = "Run the verification command: `%s`. Fix any failures before proceeding."
          """
          .formatted(methodology.verify());
    }

    return """
        ---
        name: verify
        description: Run the project verification command
        ---
        Run the verification command: `%s`

        If any tests or checks fail:
        1. Analyze the failure
        2. Fix the root cause
        3. Re-run verification until it passes

        Do not consider the task complete until verification passes.
        """
        .formatted(methodology.verify());
  }
}
