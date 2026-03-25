/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.gen;

import static org.junit.jupiter.api.Assertions.*;

import ai.singlr.sing.config.SecurityAudit;
import ai.singlr.sing.config.SingYaml;
import java.util.List;
import org.junit.jupiter.api.Test;

class SecurityAuditGeneratorTest {

  @Test
  void auditScriptContainsAuditorBinary() {
    var script = SecurityAuditGenerator.generateAuditScript("codex");

    assertTrue(script.contains("codex --print"));
  }

  @Test
  void auditScriptContainsOWASPChecklist() {
    var script = SecurityAuditGenerator.generateAuditScript("codex");

    assertTrue(script.contains("OWASP Top 10"));
    assertTrue(script.contains("Hardcoded secrets"));
    assertTrue(script.contains("input validation"));
    assertTrue(script.contains("command injection"));
    assertTrue(script.contains("path traversal"));
    assertTrue(script.contains("SQL injection"));
    assertTrue(script.contains("SSRF"));
  }

  @Test
  void auditScriptUsesGitDiff() {
    var script = SecurityAuditGenerator.generateAuditScript("codex");

    assertTrue(script.contains("git diff main..HEAD"));
  }

  @Test
  void auditScriptExcludesLockFiles() {
    var script = SecurityAuditGenerator.generateAuditScript("codex");

    assertTrue(script.contains("':!*.lock'"));
    assertTrue(script.contains("':!*.min.js'"));
    assertTrue(script.contains("':!*.min.css'"));
  }

  @Test
  void auditScriptWritesResultFile() {
    var script = SecurityAuditGenerator.generateAuditScript("codex");

    assertTrue(script.contains("~/security-audit.md"));
  }

  @Test
  void auditScriptExitsWithCode2OnFailure() {
    var script = SecurityAuditGenerator.generateAuditScript("codex");

    assertTrue(script.contains("exit 2"));
  }

  @Test
  void auditScriptUsesClaudeBinaryWhenConfigured() {
    var script = SecurityAuditGenerator.generateAuditScript("claude");

    assertTrue(script.contains("claude --print"));
    assertFalse(script.contains("codex"));
  }

  @Test
  void claudeCodeHooksConfigIsValidJson() {
    var config =
        PostTaskHooksGenerator.generateClaudeCodeHooksConfig("/home/dev/.sing/security-audit.sh");

    assertTrue(config.contains("\"hooks\""));
    assertTrue(config.contains("\"Stop\""));
    assertTrue(config.contains("/home/dev/.sing/security-audit.sh"));
    assertTrue(config.contains("\"type\": \"command\""));
  }

  @Test
  void codexHooksConfigIsValidToml() {
    var config =
        PostTaskHooksGenerator.generateCodexHooksConfig("/home/dev/.sing/security-audit.sh");

    assertTrue(config.contains("[[hooks]]"));
    assertTrue(config.contains("event = \"session_stop\""));
    assertTrue(config.contains("/home/dev/.sing/security-audit.sh"));
  }

  @Test
  void geminiHooksConfigIsValidJson() {
    var config =
        PostTaskHooksGenerator.generateGeminiHooksConfig("/home/dev/.sing/security-audit.sh");

    assertTrue(config.contains("\"hooks\""));
    assertTrue(config.contains("\"SessionEnd\""));
    assertTrue(config.contains("\"post-task-audit\""));
    assertTrue(config.contains("/home/dev/.sing/security-audit.sh"));
  }

  @Test
  void generateFilesReturnsEmptyWhenNoSecurityAudit() {
    var agent =
        new SingYaml.Agent(
            "claude-code", true, "sing/", true, null, null, null, null, null, null, null);
    var config = minimalConfig(agent);

    var files = SecurityAuditGenerator.generateFiles(config);

    assertTrue(files.isEmpty());
  }

  @Test
  void generateFilesReturnsEmptyWhenDisabled() {
    var audit = new SecurityAudit(false, "codex");
    var agent =
        new SingYaml.Agent(
            "claude-code", true, "sing/", true, null, null, null, null, audit, null, null);
    var config = minimalConfig(agent);

    var files = SecurityAuditGenerator.generateFiles(config);

    assertTrue(files.isEmpty());
  }

  @Test
  void generateFilesReturnsTwoFilesForClaudeCode() {
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

    var files = SecurityAuditGenerator.generateFiles(config);

    assertEquals(1, files.size());

    var script = files.get(0);
    assertTrue(script.remotePath().endsWith("security-audit.sh"));
    assertTrue(script.executable());
    assertTrue(script.content().contains("codex --print"));
  }

  @Test
  void generateFilesReturnsTwoFilesForCodex() {
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

    var files = SecurityAuditGenerator.generateFiles(config);

    assertEquals(1, files.size());
    assertTrue(files.get(0).content().contains("claude --print"));
  }

  @Test
  void generateFilesReturnsTwoFilesForGemini() {
    var audit = new SecurityAudit(true, "codex");
    var agent =
        new SingYaml.Agent(
            "gemini", true, "sing/", true, List.of("codex"), null, null, null, audit, null, null);
    var config = minimalConfig(agent);

    var files = SecurityAuditGenerator.generateFiles(config);

    assertEquals(1, files.size());
    assertTrue(files.get(0).remotePath().endsWith("security-audit.sh"));
  }

  @Test
  void generateFilesUsesCorrectSshUser() {
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

    var files = SecurityAuditGenerator.generateFiles(config);

    assertTrue(files.get(0).remotePath().contains("/home/alice/"));
  }

  @Test
  void generateFilesAutoResolvesAuditorFromInstallList() {
    var audit = new SecurityAudit(true, null);
    var agent =
        new SingYaml.Agent(
            "claude-code",
            true,
            "sing/",
            true,
            List.of("claude-code", "codex"),
            null,
            null,
            null,
            audit,
            null,
            null);
    var config = minimalConfig(agent);

    var files = SecurityAuditGenerator.generateFiles(config);

    assertEquals(1, files.size());
    assertTrue(files.get(0).content().contains("codex --print"));
  }

  @Test
  void generateFilesAutoResolvesFirstNonPrimary() {
    var audit = new SecurityAudit(true, null);
    var agent =
        new SingYaml.Agent(
            "codex",
            true,
            "sing/",
            true,
            List.of("gemini", "claude-code"),
            null,
            null,
            null,
            audit,
            null,
            null);
    var config = minimalConfig(agent);

    var files = SecurityAuditGenerator.generateFiles(config);

    assertEquals(1, files.size());
    assertTrue(files.get(0).content().contains("gemini --print"));
  }

  @Test
  void generateFilesSelfAuditsWhenOnlyPrimaryInstalled() {
    var audit = new SecurityAudit(true, null);
    var agent =
        new SingYaml.Agent(
            "claude-code",
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

    var files = SecurityAuditGenerator.generateFiles(config);

    assertEquals(1, files.size());
    assertTrue(files.get(0).content().contains("claude --print"));
  }

  @Test
  void generateFilesReturnsEmptyWhenEnabledButNoInstallList() {
    var audit = new SecurityAudit(true, null);
    var agent =
        new SingYaml.Agent(
            "claude-code", true, "sing/", true, null, null, null, null, audit, null, null);
    var config = minimalConfig(agent);

    var files = SecurityAuditGenerator.generateFiles(config);

    assertTrue(files.isEmpty());
  }

  @Test
  void generateFilesThrowsWhenAuditorIsUnknown() {
    var audit = new SecurityAudit(true, "unknown-agent");
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

    var ex =
        assertThrows(
            IllegalArgumentException.class, () -> SecurityAuditGenerator.generateFiles(config));

    assertTrue(ex.getMessage().contains("Unknown security auditor"));
  }

  @Test
  void generateFilesAllowsSelfAuditWhenAuditorSameAsPrimary() {
    var audit = new SecurityAudit(true, "claude-code");
    var agent =
        new SingYaml.Agent(
            "claude-code",
            true,
            "sing/",
            true,
            List.of("claude-code", "codex"),
            null,
            null,
            null,
            audit,
            null,
            null);
    var config = minimalConfig(agent);

    var files = SecurityAuditGenerator.generateFiles(config);

    assertEquals(1, files.size());
    assertTrue(files.get(0).content().contains("claude --print"));
  }

  @Test
  void generateFilesThrowsWhenAuditorNotInstalled() {
    var audit = new SecurityAudit(true, "gemini");
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

    var ex =
        assertThrows(
            IllegalArgumentException.class, () -> SecurityAuditGenerator.generateFiles(config));

    assertTrue(ex.getMessage().contains("is not in the agent install list"));
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
