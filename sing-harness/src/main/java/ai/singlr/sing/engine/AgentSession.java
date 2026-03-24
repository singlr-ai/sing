/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import ai.singlr.sing.config.YamlUtil;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * Manages headless agent session state inside a container. All operations go through {@link
 * ContainerExec#asDevUser} to execute commands as the dev user.
 */
public final class AgentSession {

  private static final String SING_DIR = "/home/dev/.sing";
  private static final String PID_FILE = SING_DIR + "/agent.pid";
  private static final String LOG_FILE = SING_DIR + "/agent.log";
  private static final String SESSION_FILE = SING_DIR + "/agent-session.json";
  private static final String TASK_FILE = SING_DIR + "/agent-task.txt";

  private final ShellExec shell;

  public AgentSession(ShellExec shell) {
    this.shell = shell;
  }

  /** Session status information. */
  public record SessionInfo(
      boolean running, int pid, String task, String startedAt, String branch, String logPath) {}

  /** Ensures the ~/.sing directory exists inside the container. */
  public void ensureDirectory(String containerName)
      throws IOException, InterruptedException, TimeoutException {
    var cmd = ContainerExec.asDevUser(containerName, List.of("mkdir", "-p", SING_DIR));
    var result = shell.exec(cmd);
    if (!result.ok()) {
      throw new IOException("Failed to create " + SING_DIR + ": " + result.stderr());
    }
  }

  /**
   * Writes the task text to a file inside the container. Uses printf with a positional argument to
   * avoid heredoc injection (content containing the delimiter could escape the heredoc).
   */
  public void writeTaskFile(String containerName, String task)
      throws IOException, InterruptedException, TimeoutException {
    var cmd =
        ContainerExec.asDevUser(
            containerName,
            List.of("bash", "-c", "printf '%s' \"$1\" > " + TASK_FILE, "bash", task));
    var result = shell.exec(cmd);
    if (!result.ok()) {
      throw new IOException("Failed to write task file: " + result.stderr());
    }
  }

  /** Writes session metadata JSON inside the container. */
  public void writeSession(String containerName, String task, String branch)
      throws IOException, InterruptedException, TimeoutException {
    var map = new LinkedHashMap<String, Object>();
    map.put("task", task);
    map.put("branch", branch);
    map.put("started_at", Instant.now().toString());
    map.put("log_path", LOG_FILE);
    var json = YamlUtil.dumpJson(map);
    var cmd =
        ContainerExec.asDevUser(
            containerName,
            List.of("bash", "-c", "printf '%s' \"$1\" > " + SESSION_FILE, "bash", json));
    var result = shell.exec(cmd);
    if (!result.ok()) {
      throw new IOException("Failed to write session metadata: " + result.stderr());
    }
  }

  /** Queries the current agent session status. Returns null if no session exists. */
  @SuppressWarnings("unchecked")
  public SessionInfo queryStatus(String containerName)
      throws IOException, InterruptedException, TimeoutException {
    var pidCmd = ContainerExec.asDevUser(containerName, List.of("cat", PID_FILE));
    var pidResult = shell.exec(pidCmd);
    if (!pidResult.ok() || pidResult.stdout().isBlank()) {
      return null;
    }

    var pid = 0;
    try {
      pid = Integer.parseInt(pidResult.stdout().trim());
    } catch (NumberFormatException e) {
      return null;
    }

    var aliveCmd =
        ContainerExec.asDevUser(containerName, List.of("kill", "-0", String.valueOf(pid)));
    var alive = shell.exec(aliveCmd).ok();

    var sessionCmd = ContainerExec.asDevUser(containerName, List.of("cat", SESSION_FILE));
    var sessionResult = shell.exec(sessionCmd);
    var task = "";
    var startedAt = "";
    var branch = "";
    if (sessionResult.ok() && !sessionResult.stdout().isBlank()) {
      var meta = (Map<String, Object>) YamlUtil.parseMap(sessionResult.stdout());
      task = Objects.toString(meta.get("task"), "");
      startedAt = Objects.toString(meta.get("started_at"), "");
      branch = Objects.toString(meta.get("branch"), "");
    }

    return new SessionInfo(alive, pid, task, startedAt, branch, LOG_FILE);
  }

  /** Kills a running agent process inside the container. SIGTERM first, then SIGKILL. */
  public void killAgent(String containerName)
      throws IOException, InterruptedException, TimeoutException {
    var pidCmd = ContainerExec.asDevUser(containerName, List.of("cat", PID_FILE));
    var pidResult = shell.exec(pidCmd);
    if (!pidResult.ok() || pidResult.stdout().isBlank()) {
      return;
    }

    var pidStr = pidResult.stdout().trim();
    try {
      Integer.parseInt(pidStr);
    } catch (NumberFormatException e) {
      return;
    }

    shell.exec(ContainerExec.asDevUser(containerName, List.of("kill", pidStr)));

    shell.exec(ContainerExec.asDevUser(containerName, List.of("sleep", "3")));

    var aliveCmd = ContainerExec.asDevUser(containerName, List.of("kill", "-0", pidStr));
    if (shell.exec(aliveCmd).ok()) {
      shell.exec(ContainerExec.asDevUser(containerName, List.of("kill", "-9", pidStr)));
    }

    shell.exec(ContainerExec.asDevUser(containerName, List.of("rm", "-f", PID_FILE)));
  }

  /**
   * Builds the SSH command for launching an agent in detached/background mode. The task is read
   * from a file inside the container to avoid shell escaping issues.
   *
   * @param agentCli the agent CLI enum (determines headless command syntax)
   */
  public static List<String> buildBackgroundLaunchCommand(
      String containerName,
      String sshUser,
      String workDir,
      boolean fullPermissions,
      AgentCli agentCli) {
    var user = Objects.requireNonNullElse(sshUser, "dev");
    var cli = Objects.requireNonNullElse(agentCli, AgentCli.CLAUDE_CODE);
    var agentCmd = cli.headlessCommand(TASK_FILE, fullPermissions);
    var script =
        "mkdir -p "
            + SING_DIR
            + " && cd "
            + workDir
            + " && nohup bash -c '"
            + agentCmd
            + " > "
            + LOG_FILE
            + " 2>&1 & echo $! > "
            + PID_FILE
            + "'";
    return List.of("ssh", user + "@" + containerName, "--", script);
  }

  /**
   * Builds the SSH command for launching an agent in interactive headless mode (foreground, with
   * task). The task is read from a file to avoid escaping issues.
   *
   * @param agentCli the agent CLI enum (determines headless command syntax)
   */
  public static List<String> buildForegroundTaskCommand(
      String containerName,
      String sshUser,
      String workDir,
      boolean fullPermissions,
      AgentCli agentCli) {
    var user = Objects.requireNonNullElse(sshUser, "dev");
    var cli = Objects.requireNonNullElse(agentCli, AgentCli.CLAUDE_CODE);
    var agentCmd = cli.headlessCommand(TASK_FILE, fullPermissions);
    var script = "cd " + workDir + " && " + agentCmd;
    return List.of("ssh", "-t", user + "@" + containerName, "--", script);
  }

  /** Returns the path to the agent log file inside the container. */
  public static String logPath() {
    return LOG_FILE;
  }
}
