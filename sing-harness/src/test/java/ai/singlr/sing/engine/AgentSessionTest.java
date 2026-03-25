/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AgentSessionTest {

  @Test
  void ensureDirectoryRunsCorrectCommand() throws Exception {
    var shell = new ScriptedShellExecutor(new ShellExec.Result(0, "", ""));
    var session = new AgentSession(shell);

    session.ensureDirectory("acme-health");

    var cmds = shell.invocations();
    assertEquals(1, cmds.size());
    assertTrue(cmds.getFirst().contains("mkdir -p /home/dev/.sing"));
    assertTrue(cmds.getFirst().contains("acme-health"));
  }

  @Test
  void queryStatusWhenRunning() throws Exception {
    var shell =
        new ScriptedShellExecutor()
            .onOk("cat /home/dev/.sing/agent.pid", "12345\n")
            .onOk("kill -0 12345", "")
            .onOk(
                "cat /home/dev/.sing/agent-session.json",
                """
                {"task":"implement auth","started_at":"2026-02-21T03:00:00Z","branch":"sing/snap-20260221","log_path":"/home/dev/.sing/agent.log"}
                """);
    var session = new AgentSession(shell);

    var info = session.queryStatus("acme-health");

    assertNotNull(info);
    assertTrue(info.running());
    assertEquals(12345, info.pid());
    assertEquals("implement auth", info.task());
    assertEquals("2026-02-21T03:00:00Z", info.startedAt());
    assertEquals("sing/snap-20260221", info.branch());
    assertEquals("/home/dev/.sing/agent.log", info.logPath());
  }

  @Test
  void queryStatusWhenStopped() throws Exception {
    var shell =
        new ScriptedShellExecutor()
            .onOk("cat /home/dev/.sing/agent.pid", "12345\n")
            .onFail("kill -0 12345", "No such process")
            .onOk(
                "cat /home/dev/.sing/agent-session.json",
                """
                {"task":"implement auth","started_at":"2026-02-21T03:00:00Z","branch":"","log_path":"/home/dev/.sing/agent.log"}
                """);
    var session = new AgentSession(shell);

    var info = session.queryStatus("acme-health");

    assertNotNull(info);
    assertFalse(info.running());
    assertEquals(12345, info.pid());
  }

  @Test
  void queryStatusWhenNoPidFile() throws Exception {
    var shell = new ScriptedShellExecutor().onFail("cat /home/dev/.sing/agent.pid", "No such file");
    var session = new AgentSession(shell);

    var info = session.queryStatus("acme-health");

    assertNull(info);
  }

  @Test
  void queryStatusWhenPidFileEmpty() throws Exception {
    var shell = new ScriptedShellExecutor().onOk("cat /home/dev/.sing/agent.pid", "");
    var session = new AgentSession(shell);

    var info = session.queryStatus("acme-health");

    assertNull(info);
  }

  @Test
  void queryStatusWhenPidNotANumber() throws Exception {
    var shell = new ScriptedShellExecutor().onOk("cat /home/dev/.sing/agent.pid", "not-a-number\n");
    var session = new AgentSession(shell);

    var info = session.queryStatus("acme-health");

    assertNull(info);
  }

  @Test
  void killAgentSendsTermThenKill() throws Exception {
    var shell =
        new ScriptedShellExecutor(new ShellExec.Result(0, "", ""))
            .onOk("cat /home/dev/.sing/agent.pid", "9999\n");
    var session = new AgentSession(shell);

    session.killAgent("acme-health");

    var cmds = shell.invocations();
    assertTrue(cmds.stream().anyMatch(c -> c.contains("kill 9999")));
    assertTrue(cmds.stream().anyMatch(c -> c.contains("sleep 3")));
    assertTrue(cmds.stream().anyMatch(c -> c.contains("kill -9 9999")));
    assertTrue(cmds.stream().anyMatch(c -> c.contains("rm -f")));
  }

  @Test
  void killAgentCleanTermination() throws Exception {
    var shell =
        new ScriptedShellExecutor(new ShellExec.Result(0, "", ""))
            .onOk("cat /home/dev/.sing/agent.pid", "9999\n")
            .onFail("kill -0 9999", "No such process");
    var session = new AgentSession(shell);

    session.killAgent("acme-health");

    var cmds = shell.invocations();
    assertFalse(cmds.stream().anyMatch(c -> c.contains("kill -9 9999")));
    assertTrue(cmds.stream().anyMatch(c -> c.contains("rm -f")));
  }

  @Test
  void killAgentIgnoresNonNumericPid() throws Exception {
    var shell =
        new ScriptedShellExecutor(new ShellExec.Result(0, "", ""))
            .onOk("cat /home/dev/.sing/agent.pid", "; rm -rf /\n");
    var session = new AgentSession(shell);

    session.killAgent("acme-health");

    var cmds = shell.invocations();
    assertEquals(1, cmds.size());
    assertFalse(cmds.stream().anyMatch(c -> c.contains("kill")));
  }

  @Test
  void killAgentNoPidFileIsNoOp() throws Exception {
    var shell = new ScriptedShellExecutor().onFail("cat /home/dev/.sing/agent.pid", "No such file");
    var session = new AgentSession(shell);

    session.killAgent("acme-health");

    assertEquals(1, shell.invocations().size());
  }

  @Test
  void buildBackgroundLaunchCommandStructure() {
    var cmd =
        AgentSession.buildBackgroundLaunchCommand(
            "acme", "dev", "/home/dev/workspace", false, AgentCli.CLAUDE_CODE);

    assertEquals("ssh", cmd.getFirst());
    assertTrue(cmd.get(1).contains("dev@acme"));
    var script = cmd.get(3);
    assertTrue(script.contains("nohup"));
    assertTrue(script.contains("claude --print"));
    assertTrue(script.contains("agent.log"));
    assertTrue(script.contains("agent.pid"));
    assertTrue(script.contains("agent-task.txt"));
    assertFalse(script.contains("--dangerously-skip-permissions"));
  }

  @Test
  void buildBackgroundLaunchCommandWithPermissions() {
    var cmd =
        AgentSession.buildBackgroundLaunchCommand(
            "acme", "dev", "/home/dev/workspace", true, AgentCli.CLAUDE_CODE);

    var script = cmd.get(3);
    assertTrue(script.contains("--dangerously-skip-permissions"));
  }

  @Test
  void buildBackgroundLaunchCommandCodexUsesExec() {
    var cmd =
        AgentSession.buildBackgroundLaunchCommand(
            "acme", "dev", "/home/dev/workspace", false, AgentCli.CODEX);

    var script = cmd.get(3);
    assertTrue(script.contains("codex exec"));
    assertFalse(script.contains("--print"));
    assertFalse(script.contains("claude"));
  }

  @Test
  void buildBackgroundLaunchCommandCodexFullAuto() {
    var cmd =
        AgentSession.buildBackgroundLaunchCommand(
            "acme", "dev", "/home/dev/workspace", true, AgentCli.CODEX);

    var script = cmd.get(3);
    assertTrue(script.contains("codex exec --full-auto"));
  }

  @Test
  void buildBackgroundLaunchCommandGeminiUsesYolo() {
    var cmd =
        AgentSession.buildBackgroundLaunchCommand(
            "acme", "dev", "/home/dev/workspace", true, AgentCli.GEMINI);

    var script = cmd.get(3);
    assertTrue(script.contains("gemini --yolo"));
    assertTrue(script.contains("-p"));
    assertFalse(script.contains("--print"));
  }

  @Test
  void buildBackgroundLaunchCommandDefaultsToClaudeCode() {
    var cmd =
        AgentSession.buildBackgroundLaunchCommand(
            "acme", "dev", "/home/dev/workspace", false, null);

    var script = cmd.get(3);
    assertTrue(script.contains("claude --print"));
  }

  @Test
  void buildForegroundTaskCommandStructure() {
    var cmd =
        AgentSession.buildForegroundTaskCommand(
            "acme", "dev", "/home/dev/workspace", false, AgentCli.CLAUDE_CODE);

    assertEquals("ssh", cmd.getFirst());
    assertTrue(cmd.contains("-t"));
    var script = cmd.get(4);
    assertTrue(script.contains("claude --print"));
    assertTrue(script.contains("agent-task.txt"));
  }

  @Test
  void buildForegroundTaskCommandCodexExec() {
    var cmd =
        AgentSession.buildForegroundTaskCommand(
            "acme", "dev", "/home/dev/workspace", true, AgentCli.CODEX);

    var script = cmd.get(4);
    assertTrue(script.contains("codex exec --full-auto"));
    assertFalse(script.contains("claude"));
  }

  @Test
  void buildForegroundTaskCommandGeminiYolo() {
    var cmd =
        AgentSession.buildForegroundTaskCommand(
            "acme", "dev", "/home/dev/workspace", true, AgentCli.GEMINI);

    var script = cmd.get(4);
    assertTrue(script.contains("gemini --yolo"));
    assertTrue(script.contains("-p"));
    assertFalse(script.contains("claude"));
  }

  @Test
  void buildForegroundTaskCommandDefaultsToClaudeCode() {
    var cmd =
        AgentSession.buildForegroundTaskCommand("acme", "dev", "/home/dev/workspace", false, null);

    var script = cmd.get(4);
    assertTrue(script.contains("claude --print"));
  }

  @Test
  void writeSessionExecutesCommand() throws Exception {
    var shell = new ScriptedShellExecutor(new ShellExec.Result(0, "", ""));
    var session = new AgentSession(shell);

    session.writeSession("acme", "implement auth", "sing/snap-20260221");

    var cmds = shell.invocations();
    assertEquals(1, cmds.size());
    assertTrue(cmds.getFirst().contains("agent-session.json"));
  }

  @Test
  void writeTaskFileExecutesCommand() throws Exception {
    var shell = new ScriptedShellExecutor(new ShellExec.Result(0, "", ""));
    var session = new AgentSession(shell);

    session.writeTaskFile("acme", "implement the payment webhook");

    var cmds = shell.invocations();
    assertEquals(1, cmds.size());
    assertTrue(cmds.getFirst().contains("agent-task.txt"));
  }
}
