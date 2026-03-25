/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.gen;

import static org.junit.jupiter.api.Assertions.*;

import ai.singlr.sing.config.CodeReview;
import ai.singlr.sing.config.SecurityAudit;
import ai.singlr.sing.config.SingYaml;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PostTaskHooksGeneratorTest {

  @Test
  void generateFilesReturnsEmptyWhenNoAgent() {
    var config =
        new SingYaml(
            "test",
            null,
            new SingYaml.Resources(2, "4GB", "50GB"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    var files = PostTaskHooksGenerator.generateFiles(config, Set.of());

    assertTrue(files.isEmpty());
  }

  @Test
  void generateFilesReturnsEmptyWhenNoAuditsEnabled() {
    var agent =
        new SingYaml.Agent(
            "claude-code", true, "sing/", true, null, null, null, null, null, null, null);
    var config = minimalConfig(agent);

    var files = PostTaskHooksGenerator.generateFiles(config, Set.of());

    assertTrue(files.isEmpty());
  }

  @Test
  void singleAuditProducesDirectHookForClaudeCode() {
    var audit = new SecurityAudit(true, "codex");
    var agent =
        new SingYaml.Agent(
            "claude-code",
            true,
            "sing/",
            true,
            List.of("codex"),
            null,
            null,
            null,
            audit,
            null,
            null);
    var config = minimalConfig(agent);

    var files = PostTaskHooksGenerator.generateFiles(config, Set.of());

    assertEquals(1, files.size());
    var hooks = files.getFirst();
    assertTrue(hooks.remotePath().endsWith("settings.json"));
    assertFalse(hooks.executable());
    assertTrue(hooks.content().contains("\"Stop\""));
    assertTrue(hooks.content().contains("security-audit.sh"));
  }

  @Test
  void singleAuditProducesDirectHookForCodex() {
    var audit = new SecurityAudit(true, "claude-code");
    var agent =
        new SingYaml.Agent(
            "codex",
            true,
            "sing/",
            true,
            List.of("claude-code"),
            null,
            null,
            null,
            audit,
            null,
            null);
    var config = minimalConfig(agent);

    var files = PostTaskHooksGenerator.generateFiles(config, Set.of());

    assertEquals(1, files.size());
    assertTrue(files.getFirst().remotePath().endsWith("codex.toml"));
    assertTrue(files.getFirst().content().contains("session_stop"));
  }

  @Test
  void singleAuditProducesDirectHookForGemini() {
    var audit = new SecurityAudit(true, "codex");
    var agent =
        new SingYaml.Agent(
            "gemini", true, "sing/", true, List.of("codex"), null, null, null, audit, null, null);
    var config = minimalConfig(agent);

    var files = PostTaskHooksGenerator.generateFiles(config, Set.of());

    assertEquals(1, files.size());
    assertTrue(files.getFirst().remotePath().contains(".gemini/settings.json"));
    assertTrue(files.getFirst().content().contains("SessionEnd"));
  }

  @Test
  void singleCodeReviewProducesDirectHook() {
    var review = new CodeReview(true, "gemini");
    var agent =
        new SingYaml.Agent(
            "claude-code",
            true,
            "sing/",
            true,
            List.of("gemini"),
            null,
            null,
            null,
            null,
            review,
            null);
    var config = minimalConfig(agent);

    var files = PostTaskHooksGenerator.generateFiles(config, Set.of());

    assertEquals(1, files.size());
    assertTrue(files.getFirst().content().contains("code-review.sh"));
  }

  @Test
  void bothAuditsProduceOrchestratorPlusHook() {
    var audit = new SecurityAudit(true, "codex");
    var review = new CodeReview(true, "gemini");
    var agent =
        new SingYaml.Agent(
            "claude-code",
            true,
            "sing/",
            true,
            List.of("codex", "gemini"),
            null,
            null,
            null,
            audit,
            review,
            null);
    var config = minimalConfig(agent);

    var files = PostTaskHooksGenerator.generateFiles(config, Set.of("codex"));

    assertEquals(2, files.size());

    var orchestrator = files.get(0);
    assertTrue(orchestrator.remotePath().endsWith("post-task.sh"));
    assertTrue(orchestrator.executable());
    assertTrue(orchestrator.content().contains("security-audit.sh"));
    assertTrue(orchestrator.content().contains("code-review.sh"));
    assertTrue(orchestrator.content().contains("PIDS"));

    var hooks = files.get(1);
    assertTrue(hooks.remotePath().endsWith("settings.json"));
    assertFalse(hooks.executable());
    assertTrue(hooks.content().contains("post-task.sh"));
  }

  @Test
  void orchestratorScriptRunsScriptsInParallel() {
    var script =
        PostTaskHooksGenerator.generateOrchestratorScript(
            List.of("/home/dev/.sing/security-audit.sh", "/home/dev/.sing/code-review.sh"));

    assertTrue(script.contains("/home/dev/.sing/security-audit.sh &"));
    assertTrue(script.contains("/home/dev/.sing/code-review.sh &"));
    assertTrue(script.contains("PIDS+=($!)"));
    assertTrue(script.contains("wait \"$pid\""));
    assertTrue(script.contains("exit $((FAILED > 0 ? 2 : 0))"));
  }

  @Test
  void hooksConfigPathClaudeCode() {
    var path = PostTaskHooksGenerator.hooksConfigPath("claude-code", "dev");

    assertEquals("/home/dev/workspace/.claude/settings.json", path);
  }

  @Test
  void hooksConfigPathCodex() {
    var path = PostTaskHooksGenerator.hooksConfigPath("codex", "dev");

    assertEquals("/home/dev/workspace/codex.toml", path);
  }

  @Test
  void hooksConfigPathGemini() {
    var path = PostTaskHooksGenerator.hooksConfigPath("gemini", "dev");

    assertEquals("/home/dev/workspace/.gemini/settings.json", path);
  }

  @Test
  void hooksConfigPathUnknownReturnsNull() {
    var path = PostTaskHooksGenerator.hooksConfigPath("unknown-agent", "dev");

    assertNull(path);
  }

  @Test
  void claudeCodeHooksConfigContainsStopEvent() {
    var config =
        PostTaskHooksGenerator.generateClaudeCodeHooksConfig("/home/dev/.sing/post-task.sh");

    assertTrue(config.contains("\"hooks\""));
    assertTrue(config.contains("\"Stop\""));
    assertTrue(config.contains("/home/dev/.sing/post-task.sh"));
    assertTrue(config.contains("\"type\": \"command\""));
  }

  @Test
  void codexHooksConfigIsValidToml() {
    var config = PostTaskHooksGenerator.generateCodexHooksConfig("/home/dev/.sing/post-task.sh");

    assertTrue(config.contains("[[hooks]]"));
    assertTrue(config.contains("event = \"session_stop\""));
    assertTrue(config.contains("/home/dev/.sing/post-task.sh"));
  }

  @Test
  void geminiHooksConfigIsValidJson() {
    var config = PostTaskHooksGenerator.generateGeminiHooksConfig("/home/dev/.sing/post-task.sh");

    assertTrue(config.contains("\"hooks\""));
    assertTrue(config.contains("\"SessionEnd\""));
    assertTrue(config.contains("\"post-task-audit\""));
    assertTrue(config.contains("/home/dev/.sing/post-task.sh"));
  }

  @Test
  void usesCorrectSshUserInPaths() {
    var audit = new SecurityAudit(true, "codex");
    var agent =
        new SingYaml.Agent(
            "claude-code",
            true,
            "sing/",
            true,
            List.of("codex"),
            null,
            null,
            null,
            audit,
            null,
            null);
    var ssh = new SingYaml.Ssh("alice", null);
    var config =
        new SingYaml(
            "test",
            null,
            new SingYaml.Resources(2, "4GB", "50GB"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            agent,
            null,
            ssh);

    var files = PostTaskHooksGenerator.generateFiles(config, Set.of());

    assertTrue(files.getFirst().remotePath().contains("/home/alice/"));
  }

  @Test
  void disabledSecurityAuditNotCollected() {
    var audit = new SecurityAudit(false, "codex");
    var review = new CodeReview(true, "gemini");
    var agent =
        new SingYaml.Agent(
            "claude-code",
            true,
            "sing/",
            true,
            List.of("codex", "gemini"),
            null,
            null,
            null,
            audit,
            review,
            null);
    var config = minimalConfig(agent);

    var files = PostTaskHooksGenerator.generateFiles(config, Set.of());

    assertEquals(1, files.size());
    assertTrue(files.getFirst().content().contains("code-review.sh"));
  }

  private static SingYaml minimalConfig(SingYaml.Agent agent) {
    return new SingYaml(
        "test",
        null,
        new SingYaml.Resources(2, "4GB", "50GB"),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        agent,
        null,
        null);
  }
}
