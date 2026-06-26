/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.singlr.sail.config.SailYaml;
import ai.singlr.sail.engine.AgentCli;
import org.junit.jupiter.api.Test;

class LanguageSkillGeneratorTest {

  private static final String BASE = "/home/dev/workspace/";

  @Test
  void nullRuntimesGeneratesNothing() {
    assertTrue(LanguageSkillGenerator.generateFiles(AgentCli.CLAUDE_CODE, null, BASE).isEmpty());
  }

  @Test
  void jdkProducesAJavaSkillForClaude() {
    var files =
        LanguageSkillGenerator.generateFiles(
            AgentCli.CLAUDE_CODE, new SailYaml.Runtimes(25, null, null), BASE);

    assertEquals(1, files.size());
    var skill = files.getFirst();
    assertEquals(BASE + ".claude/skills/java/SKILL.md", skill.remotePath());
    assertFalse(skill.executable());
    assertEquals(GeneratedFile.Ownership.SAIL, skill.ownership(), "language skills are sail-owned");
    assertTrue(skill.content().startsWith("---\n"));
    assertTrue(skill.content().contains("name: java"));
    assertTrue(skill.content().contains("Java (JDK 25) Standards"));
    assertTrue(skill.content().contains("Records for all value types"));
  }

  @Test
  void jdkVersionFlowsIntoTheSkill() {
    var files =
        LanguageSkillGenerator.generateFiles(
            AgentCli.CLAUDE_CODE, new SailYaml.Runtimes(21, null, null), BASE);

    assertTrue(files.getFirst().content().contains("Java (JDK 21) Standards"));
    assertTrue(files.getFirst().content().contains("JDK 21)"));
  }

  @Test
  void nodeProducesATypescriptSkill() {
    var files =
        LanguageSkillGenerator.generateFiles(
            AgentCli.CLAUDE_CODE, new SailYaml.Runtimes(0, "22", null), BASE);

    assertEquals(1, files.size());
    var skill = files.getFirst();
    assertEquals(BASE + ".claude/skills/typescript/SKILL.md", skill.remotePath());
    assertTrue(skill.content().contains("name: typescript"));
    assertTrue(skill.content().contains("Node.js / TypeScript Standards"));
    assertTrue(skill.content().contains("Functional components"));
  }

  @Test
  void bothRuntimesProduceBothSkills() {
    var files =
        LanguageSkillGenerator.generateFiles(
            AgentCli.CLAUDE_CODE, new SailYaml.Runtimes(25, "22", null), BASE);

    assertEquals(2, files.size());
    assertTrue(
        files.stream().anyMatch(f -> f.remotePath().endsWith(".claude/skills/java/SKILL.md")));
    assertTrue(
        files.stream()
            .anyMatch(f -> f.remotePath().endsWith(".claude/skills/typescript/SKILL.md")));
  }

  @Test
  void mavenOnlyRuntimeProducesNoSkills() {
    var files =
        LanguageSkillGenerator.generateFiles(
            AgentCli.CLAUDE_CODE, new SailYaml.Runtimes(0, null, "3.9.9"), BASE);

    assertTrue(files.isEmpty(), "Maven without a JDK or Node yields no language skill");
  }

  @Test
  void codexSkillsLandUnderTheAgentsDirectory() {
    var files =
        LanguageSkillGenerator.generateFiles(
            AgentCli.CODEX, new SailYaml.Runtimes(25, "22", null), BASE);

    assertTrue(
        files.stream().anyMatch(f -> f.remotePath().endsWith(".agents/skills/java/SKILL.md")));
    assertTrue(
        files.stream()
            .anyMatch(f -> f.remotePath().endsWith(".agents/skills/typescript/SKILL.md")));
  }
}
