/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.gen;

import ai.singlr.sing.engine.AgentCli;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates spec management skills for AI coding agents. When {@code agent.specs_dir} is
 * configured, produces agent-native skill files that teach the agent to create, list, update, and
 * display specs without manual YAML editing.
 *
 * <ul>
 *   <li>Claude Code: {@code .claude/skills/spec-board/SKILL.md} + supporting files
 *   <li>Gemini CLI: {@code .gemini/skills/spec-board/SKILL.md} + {@code
 *       .gemini/commands/spec/board.toml}
 *   <li>Codex: instructions embedded in {@code AGENTS.md} via {@link #codexInstructions}
 * </ul>
 */
public final class SpecSkillGenerator {

  private SpecSkillGenerator() {}

  /**
   * Generates spec skill files for the given agent when specs are configured.
   *
   * @param agent the target agent type
   * @param specsDir the specs directory name (e.g., "specs")
   * @param basePath the workspace base path (e.g., {@code /home/dev/workspace/})
   * @return generated files, empty if agent is Codex (uses inline instructions instead)
   */
  public static List<GeneratedFile> generateFiles(
      AgentCli agent, String specsDir, String basePath) {
    if (specsDir == null) {
      return List.of();
    }
    var absSpecsDir = "~/workspace/" + specsDir;

    return switch (agent) {
      case CLAUDE_CODE -> claudeSkillFiles(absSpecsDir, basePath);
      case GEMINI -> geminiSkillFiles(absSpecsDir, basePath);
      case CODEX -> List.of();
    };
  }

  /**
   * Returns spec management instructions for Codex, which has no skill system. These are appended
   * to the generated AGENTS.md content.
   *
   * @param specsDir the specs directory name
   * @return markdown instructions, or empty string if specsDir is null
   */
  public static String codexInstructions(String specsDir) {
    if (specsDir == null) {
      return "";
    }
    var absSpecsDir = "~/workspace/" + specsDir;
    return """

        ## Spec Management

        You are the spec manager for this project. When the engineer asks you to create, list, \
        update, or show specs, follow these instructions exactly.

        """
        + coreInstructions(absSpecsDir)
        + specTemplate();
  }

  private static List<GeneratedFile> claudeSkillFiles(String specsDir, String basePath) {
    var files = new ArrayList<GeneratedFile>();
    var skillDir = basePath + ".claude/skills/spec-board/";

    files.add(new GeneratedFile(skillDir + "SKILL.md", claudeSkillMd(specsDir), false));
    files.add(new GeneratedFile(skillDir + "spec-template.md", specTemplateMd(), false));

    return List.copyOf(files);
  }

  private static List<GeneratedFile> geminiSkillFiles(String specsDir, String basePath) {
    var files = new ArrayList<GeneratedFile>();

    files.add(
        new GeneratedFile(
            basePath + ".gemini/skills/spec-board/SKILL.md", geminiSkillMd(specsDir), false));
    files.add(
        new GeneratedFile(
            basePath + ".gemini/commands/spec/board.toml", geminiCommandToml(), false));

    return List.copyOf(files);
  }

  private static String claudeSkillMd(String specsDir) {
    return """
        ---
        name: spec-board
        description: >
          Manage the project spec board — create specs, list them as a kanban board, update status,
          show spec details. Use when the engineer mentions specs, tasks, the board, planning work,
          or wants to see what's pending/in-progress/done.
        argument-hint: "[create|list|show|update] [args...]"
        ---

        You are the spec manager for this project. The engineer interacts with you to plan and
        track work instead of editing YAML by hand.

        ## Commands

        ### `/spec-board list` or "show me the board"
        """
        + listInstructions(specsDir)
        + """

        ### `/spec-board create <id> <title>` or "create a spec for ..."
        """
        + createInstructions(specsDir)
        + """

        ### `/spec-board show <id>` or "show me the auth spec"
        """
        + showInstructions(specsDir)
        + """

        ### `/spec-board update <id> <status>` or "move auth to in_progress"
        """
        + updateInstructions(specsDir)
        + """

        ### Bulk creation — "turn these into specs" or "create specs for all of these"
        """
        + bulkCreateInstructions(specsDir)
        + """

        ## Reference

        """
        + coreReference(specsDir)
        + """

        ## Spec Template

        When creating `spec.md`, use the template in [spec-template.md](spec-template.md).
        """;
  }

  private static String geminiSkillMd(String specsDir) {
    return """
        ---
        name: spec-board
        description: >
          Manage the project spec board — create specs, list them as a kanban board, update status,
          show spec details. Use when the engineer mentions specs, tasks, the board, planning work,
          or wants to see what's pending/in-progress/done.
        ---

        You are the spec manager for this project. The engineer interacts with you to plan and
        track work instead of editing YAML by hand.

        ## Commands

        When the engineer says "show the board", "create a spec", "update spec status", or similar:

        ### List / Board View
        """
        + listInstructions(specsDir)
        + """

        ### Create a Spec
        """
        + createInstructions(specsDir)
        + """

        ### Show a Spec
        """
        + showInstructions(specsDir)
        + """

        ### Update Status
        """
        + updateInstructions(specsDir)
        + """

        ### Bulk Creation
        """
        + bulkCreateInstructions(specsDir)
        + """

        ## Reference

        """
        + coreReference(specsDir)
        + """

        ## Spec Template

        """
        + specTemplate();
  }

  private static String geminiCommandToml() {
    return """
        [command]
        description = "Show the spec board — list all specs grouped by status"

        [command.prompt]
        text = "Read specs/index.yaml and display all specs grouped by status as a kanban board. Show columns: Pending, In Progress, Review, Done. For each spec show id, title, and dependencies."
        """;
  }

  private static String listInstructions(String specsDir) {
    return """
        Read `%s/index.yaml` and display specs grouped by status columns:

        ```
        ┌─────────────┬─────────────────┬──────────────┬──────────────┐
        │ Pending (3)  │ In Progress (1) │ Review (0)   │ Done (2)     │
        ├─────────────┼─────────────────┼──────────────┼──────────────┤
        │ search-api   │ oauth-flow      │              │ data-model   │
        │  └─ depends: │                 │              │ auth-setup   │
        │     oauth    │                 │              │              │
        │ payments     │                 │              │              │
        │ notifications│                 │              │              │
        └─────────────┴─────────────────┴──────────────┴──────────────┘
        ```

        Show the title under each id. Mark specs whose dependencies are not yet met with \
        a lock icon or note.
        """
        .formatted(specsDir);
  }

  private static String createInstructions(String specsDir) {
    return """
        1. Derive an id from the title (lowercase, hyphens, e.g., "OAuth Flow" → `oauth-flow`)
        2. Create directory `%1$s/<id>/`
        3. Write `%1$s/<id>/spec.md` using the spec template
        4. Read current `%1$s/index.yaml`
        5. Append the new spec entry:
           ```yaml
           - id: <id>
             title: "<title>"
             status: pending
           ```
        6. Write the updated `%1$s/index.yaml`
        7. Confirm: "Created spec `<id>` — fill in the details in `%1$s/<id>/spec.md`"

        If the engineer provided detailed requirements during the conversation, fill in the \
        spec.md with those details instead of leaving placeholders.

        Ask the engineer if this spec depends on any existing specs, and set `depends_on` \
        accordingly.
        """
        .formatted(specsDir);
  }

  private static String showInstructions(String specsDir) {
    return """
        1. Read `%s/<id>/spec.md`
        2. Display the full content
        3. Also show the spec's status, assignee, and dependencies from index.yaml
        """
        .formatted(specsDir);
  }

  private static String updateInstructions(String specsDir) {
    return """
        Valid statuses: `pending`, `in_progress`, `review`, `done`

        1. Read `%1$s/index.yaml`
        2. Find the spec entry by id
        3. Update the `status` field
        4. Write the updated `%1$s/index.yaml`
        5. Confirm: "Updated `<id>` → `<new-status>`"

        When moving to `done`, ask the engineer if they want the spec directory moved to \
        `%1$s/archive/<id>/`.
        """
        .formatted(specsDir);
  }

  private static String bulkCreateInstructions(String specsDir) {
    return """
        When the engineer has brainstormed multiple features or tasks and wants to turn them \
        into specs:

        1. Extract each distinct unit of work from the conversation
        2. For each, derive an id and title
        3. Infer dependencies from the natural ordering and relationships discussed
        4. Create all spec directories and spec.md files
        5. Update `%s/index.yaml` with all new entries in dependency order
        6. Show the resulting board view

        This is the primary daytime workflow: brainstorm with the engineer, then materialize \
        the plan into specs with one confirmation.
        """
        .formatted(specsDir);
  }

  private static String coreReference(String specsDir) {
    return """
        ### index.yaml Format
        ```yaml
        specs:
          - id: oauth-flow
            title: "OAuth 2.0 authorization code flow"
            status: in_progress
            assignee: claude-code
            depends_on: []
            branch: feat/oauth-flow
          - id: search-api
            title: "Full-text search API"
            status: pending
            depends_on: [oauth-flow]
        ```

        ### Status Lifecycle
        `pending` → `in_progress` → `review` → `done`
        - **pending**: ready to be picked up
        - **in_progress**: agent is actively working on it (set by `sing dispatch`)
        - **review**: PR created, waiting for human review (set by `sing`)
        - **done**: PR merged, work complete (set by engineer via `sing`)
        Status is managed by `sing`, not by the agent. Do not modify status in index.yaml
        during autonomous execution.

        ### Important
        Specs live at `%1$s/` — an absolute path in the workspace root, NOT inside any repo.
        Never create a `specs/` directory inside a source repository.

        ### Fields
        - **id** (required): directory name, lowercase with hyphens
        - **title** (required): short human-readable description
        - **status** (required): one of pending, in_progress, review, done
        - **assignee** (optional): agent type or engineer name
        - **depends_on** (optional): list of spec ids that must be done first
        - **branch** (optional): git branch name for this spec's work

        ### Directory Structure
        ```
        %s/
        ├── index.yaml
        ├── <spec-id>/
        │   ├── spec.md        # Detailed specification
        │   └── plan.md        # Optional implementation plan
        └── archive/           # Completed specs moved here
        ```

        ### Dependency Rules
        A spec cannot be started if any of its `depends_on` specs are not `done`.
        When listing specs, visually indicate which pending specs are blocked.
        """
        .formatted(specsDir);
  }

  private static String specTemplate() {
    return "When creating a new `spec.md`, use this structure:\n\n```markdown\n"
        + specTemplateMd()
        + "```\n";
  }

  private static String specTemplateMd() {
    return """
        # <Title>

        ## Goal
        What this spec achieves in one or two sentences.

        ## Background
        Why this work is needed. Link to prior specs or decisions if relevant.

        ## Requirements
        - Concrete, testable requirements
        - Each item should be independently verifiable

        ## Approach
        High-level design and key decisions. Include:
        - Components affected
        - Data model changes (if any)
        - API contracts (if any)

        ## Edge Cases
        - Known edge cases and how to handle them

        ## Test Strategy
        - What to test and how
        - Key scenarios to cover

        ## Out of Scope
        - What this spec explicitly does NOT cover
        """;
  }

  private static String coreInstructions(String specsDir) {
    return listInstructions(specsDir)
        + createInstructions(specsDir)
        + showInstructions(specsDir)
        + updateInstructions(specsDir)
        + bulkCreateInstructions(specsDir)
        + coreReference(specsDir);
  }
}
