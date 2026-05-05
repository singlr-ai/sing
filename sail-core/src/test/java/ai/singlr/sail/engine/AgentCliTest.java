/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AgentCliTest {

  @Test
  void fromYamlNameResolvesClaudeCode() {
    var cli = AgentCli.fromYamlName("claude-code");

    assertEquals("claude-code", cli.yamlName());
    assertEquals("claude", cli.binaryName());
    assertFalse(cli.requiresNode());
    assertEquals(AgentCli.InstallMethod.NATIVE_SCRIPT, cli.method());
    assertTrue(cli.installCommand().contains("claude.ai/install.sh"));
  }

  @Test
  void fromYamlNameResolvesCodex() {
    var cli = AgentCli.fromYamlName("codex");

    assertEquals("codex", cli.yamlName());
    assertEquals("codex", cli.binaryName());
    assertTrue(cli.requiresNode());
    assertEquals(AgentCli.InstallMethod.NPM, cli.method());
    assertTrue(cli.installCommand().contains("@openai/codex"));
  }

  @Test
  void fromYamlNameResolvesGemini() {
    var cli = AgentCli.fromYamlName("gemini");

    assertEquals("gemini", cli.yamlName());
    assertEquals("gemini", cli.binaryName());
    assertTrue(cli.requiresNode());
    assertEquals(AgentCli.InstallMethod.NPM, cli.method());
    assertTrue(cli.installCommand().contains("@google/gemini-cli"));
  }

  @Test
  void fromYamlNameThrowsOnUnknown() {
    var ex =
        assertThrows(IllegalArgumentException.class, () -> AgentCli.fromYamlName("unknown-agent"));

    assertTrue(ex.getMessage().contains("Unknown agent CLI"));
    assertTrue(ex.getMessage().contains("unknown-agent"));
    assertTrue(ex.getMessage().contains("claude-code, codex, gemini"));
  }

  @Test
  void claudeCodeDoesNotRequireNode() {
    assertFalse(AgentCli.CLAUDE_CODE.requiresNode());
  }

  @Test
  void codexRequiresNode() {
    assertTrue(AgentCli.CODEX.requiresNode());
  }

  @Test
  void geminiRequiresNode() {
    assertTrue(AgentCli.GEMINI.requiresNode());
  }

  @Test
  void claudeCodeUsesNativeScript() {
    assertEquals(AgentCli.InstallMethod.NATIVE_SCRIPT, AgentCli.CLAUDE_CODE.method());
  }

  @Test
  void codexUsesNpm() {
    assertEquals(AgentCli.InstallMethod.NPM, AgentCli.CODEX.method());
  }

  @Test
  void geminiUsesNpm() {
    assertEquals(AgentCli.InstallMethod.NPM, AgentCli.GEMINI.method());
  }

  private static final String TASK = "/home/dev/.sail/agent-task.txt";

  @Test
  void headlessCommandClaudeCodeWithPermissions() {
    var cmd = AgentCli.CLAUDE_CODE.headlessCommand(TASK, true);

    assertTrue(cmd.contains("claude --print"));
    assertTrue(cmd.contains("--dangerously-skip-permissions"));
    assertTrue(cmd.contains("-p \"$(cat " + TASK + ")\""));
  }

  @Test
  void headlessCommandClaudeCodeWithoutPermissions() {
    var cmd = AgentCli.CLAUDE_CODE.headlessCommand(TASK, false);

    assertTrue(cmd.contains("claude --print"));
    assertTrue(cmd.contains("-p \"$(cat " + TASK + ")\""));
    assertFalse(cmd.contains("--dangerously-skip-permissions"));
  }

  @Test
  void headlessCommandCodexWithPermissions() {
    var cmd = AgentCli.CODEX.headlessCommand(TASK, true);

    assertTrue(cmd.contains("codex exec"));
    assertTrue(cmd.contains("--full-auto"));
    assertTrue(cmd.contains("\"$(cat " + TASK + ")\""));
    assertFalse(cmd.contains("--print"));
  }

  @Test
  void headlessCommandCodexWithoutPermissions() {
    var cmd = AgentCli.CODEX.headlessCommand(TASK, false);

    assertTrue(cmd.contains("codex exec"));
    assertFalse(cmd.contains("--full-auto"));
  }

  @Test
  void headlessCommandGeminiWithPermissions() {
    var cmd = AgentCli.GEMINI.headlessCommand(TASK, true);

    assertTrue(cmd.contains("gemini"));
    assertTrue(cmd.contains("--yolo"));
    assertTrue(cmd.contains("-p \"$(cat " + TASK + ")\""));
    assertFalse(cmd.contains("--print"));
    assertFalse(cmd.contains("exec"));
  }

  @Test
  void headlessCommandGeminiWithoutPermissions() {
    var cmd = AgentCli.GEMINI.headlessCommand(TASK, false);

    assertTrue(cmd.contains("gemini"));
    assertTrue(cmd.contains("-p"));
    assertFalse(cmd.contains("--yolo"));
  }

  @Test
  void interactiveCommandClaudeCodeWithPermissions() {
    assertEquals(
        "claude --dangerously-skip-permissions", AgentCli.CLAUDE_CODE.interactiveCommand(true));
  }

  @Test
  void interactiveCommandClaudeCodeWithoutPermissions() {
    assertEquals("claude", AgentCli.CLAUDE_CODE.interactiveCommand(false));
  }

  @Test
  void interactiveCommandCodexWithPermissions() {
    assertEquals("codex --full-auto", AgentCli.CODEX.interactiveCommand(true));
  }

  @Test
  void interactiveCommandCodexWithoutPermissions() {
    assertEquals("codex", AgentCli.CODEX.interactiveCommand(false));
  }

  @Test
  void interactiveCommandGeminiWithPermissions() {
    assertEquals("gemini --yolo", AgentCli.GEMINI.interactiveCommand(true));
  }

  @Test
  void interactiveCommandGeminiWithoutPermissions() {
    assertEquals("gemini", AgentCli.GEMINI.interactiveCommand(false));
  }

  @Test
  void displayNameClaudeCode() {
    assertEquals("Claude Code", AgentCli.CLAUDE_CODE.displayName());
  }

  @Test
  void displayNameCodex() {
    assertEquals("Codex CLI (@openai/codex)", AgentCli.CODEX.displayName());
  }

  @Test
  void displayNameGemini() {
    assertEquals("Gemini CLI (@google/gemini-cli)", AgentCli.GEMINI.displayName());
  }
}
