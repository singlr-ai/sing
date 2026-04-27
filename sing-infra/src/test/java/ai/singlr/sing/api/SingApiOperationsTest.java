/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.api;

import static org.junit.jupiter.api.Assertions.*;

import ai.singlr.sing.engine.ShellExec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SingApiOperationsTest {

  private static final String RUNNING_JSON =
      """
      [
        {
          "name": "acme",
          "status": "Running",
          "state": {
            "network": {
              "eth0": {
                "addresses": [
                  {"family": "inet", "address": "10.0.0.42", "scope": "global"}
                ]
              }
            }
          }
        }
      ]
      """;

  private static final String STOPPED_JSON =
      """
      [{"name": "acme", "status": "Stopped", "state": {}}]
      """;

  private static final String EMPTY_JSON = "[]";

  private static final String INDEX_YAML =
      """
      specs:
        - id: setup
          title: Setup project
          status: done
        - id: auth
          title: Add auth
          status: pending
          depends_on: [setup]
        - id: billing
          title: Add billing
          status: pending
          depends_on: [missing]
      """;

  private static final String INDEX_WITH_BRANCH_YAML =
      """
      specs:
        - id: auth
          title: Add auth
          status: pending
          branch: feat/custom
      """;

  @TempDir Path tempDir;

  @Test
  void healthReturnsOk() {
    var operations = new SingApiOperations(new FakeShell(), "sing.yaml");

    assertEquals("ok", operations.health().get("status"));
  }

  @Test
  void defaultConstructorSupportsHealthChecks() {
    assertEquals("ok", new SingApiOperations().health().get("status"));
  }

  @Test
  void projectReturnsContainerAndAgentStatus() throws Exception {
    var operations = operations(shell().on("incus list ^acme$", RUNNING_JSON));

    var result = operations.project("acme");

    assertEquals("acme", result.get("name"));
    assertEquals("running", result.get("container_status"));
    assertTrue(result.get("agent").toString().contains("claude-code"));
  }

  @Test
  void projectMapsStoppedMissingAndErrorStates() throws Exception {
    assertEquals(
        "stopped",
        operations(shell().on("incus list ^acme$", STOPPED_JSON))
            .project("acme")
            .get("container_status"));
    assertEquals(
        "not_created",
        operations(shell().on("incus list ^acme$", EMPTY_JSON))
            .project("acme")
            .get("container_status"));
    assertEquals(
        "error",
        operations(shell().on("incus list ^acme$", new ShellExec.Result(1, "", "boom")))
            .project("acme")
            .get("container_status"));
  }

  @Test
  void projectOmitsAgentWhenUnconfigured() throws Exception {
    var operations = operations(noAgentYaml(), shell().on("incus list ^acme$", RUNNING_JSON));

    var result = operations.project("acme");

    assertFalse(result.containsKey("agent"));
  }

  @Test
  void specsReturnsBoardSummary() throws Exception {
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML));

    var result = operations.specs("acme");

    assertEquals("acme", result.get("name"));
    assertTrue(result.get("specs") instanceof List<?>);
    assertTrue(result.toString().contains("next_ready_id=auth"));
  }

  @Test
  void specReturnsContentWhenPresent() throws Exception {
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML)
                .on("cat /home/dev/workspace/specs/auth/spec.md", "# Auth"));

    var result = operations.spec("acme", "auth");

    assertEquals(true, result.get("content_available"));
    assertEquals("# Auth", result.get("content"));
  }

  @Test
  void specReturnsNotFoundForUnknownSpec() throws Exception {
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML));

    var error = assertThrows(ApiException.class, () -> operations.spec("acme", "missing"));

    assertEquals(404, error.status());
    assertEquals("spec_not_found", error.error().code());
  }

  @Test
  void specAllowsMissingContent() throws Exception {
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML)
                .on(
                    "cat /home/dev/workspace/specs/auth/spec.md",
                    new ShellExec.Result(1, "", "No such file")));

    var result = operations.spec("acme", "auth");

    assertEquals(false, result.get("content_available"));
    assertFalse(result.containsKey("content"));
  }

  @Test
  void dispatchReturnsNoPendingSpecs() throws Exception {
    var index = "specs:\n  - id: done\n    title: Done\n    status: done\n";
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/workspace/specs/index.yaml", index));

    var result = operations.dispatch("acme", Map.of());

    assertEquals(false, result.get("dispatched"));
    assertEquals("no_pending_specs", result.get("reason"));
  }

  @Test
  void dispatchDryRunUpdatesSpecAndReturnsStructuredResult() throws Exception {
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", new ShellExec.Result(1, "", "missing"))
                .on("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML)
                .on("cat /home/dev/workspace/specs/auth/spec.md", "Do auth")
                .on("mkdir -p /home/dev/workspace/specs", "")
                .on("printf '%s'", ""));

    var result = operations.dispatch("acme", Map.of("spec_id", "auth", "dry_run", true));

    assertEquals(true, result.get("dispatched"));
    assertTrue(result.get("spec").toString().contains("status=in_progress"));
    assertTrue(result.get("agent").toString().contains("running=false"));
  }

  @Test
  void dispatchRejectsBlockedSpec() throws Exception {
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", new ShellExec.Result(1, "", "missing"))
                .on("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML));

    var error =
        assertThrows(
            ApiException.class, () -> operations.dispatch("acme", Map.of("spec_id", "billing")));

    assertEquals(409, error.status());
    assertEquals("spec_not_ready", error.error().code());
  }

  @Test
  void dispatchRejectsInvalidMode() throws Exception {
    var operations = operations(shell().on("incus list ^acme$", RUNNING_JSON));

    var error =
        assertThrows(
            ApiException.class, () -> operations.dispatch("acme", Map.of("mode", "sideways")));

    assertEquals(422, error.status());
    assertEquals("invalid_mode", error.error().code());
  }

  @Test
  void dispatchRejectsUnknownSpec() throws Exception {
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", new ShellExec.Result(1, "", "missing"))
                .on("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML));

    var error =
        assertThrows(
            ApiException.class, () -> operations.dispatch("acme", Map.of("spec_id", "missing")));

    assertEquals("spec_not_found", error.error().code());
  }

  @Test
  void dispatchRejectsRunningAgent() throws Exception {
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", "123")
                .on("kill -0 123", "")
                .on("cat /home/dev/.sing/agent-session.json", "{\"task\": \"work\"}"));

    var error = assertThrows(ApiException.class, () -> operations.dispatch("acme", Map.of()));

    assertEquals(409, error.status());
    assertEquals("agent_already_running", error.error().code());
  }

  @Test
  void projectStoppedMapsToConflict() throws Exception {
    var operations = operations(shell().on("incus list ^acme$", STOPPED_JSON));

    var error = assertThrows(ApiException.class, () -> operations.specs("acme"));

    assertEquals(409, error.status());
    assertEquals("project_stopped", error.error().code());
  }

  @Test
  void projectMissingMapsToNotFound() throws Exception {
    var operations = operations(shell().on("incus list ^acme$", EMPTY_JSON));

    var error = assertThrows(ApiException.class, () -> operations.specs("acme"));

    assertEquals(404, error.status());
    assertEquals("project_not_created", error.error().code());
  }

  @Test
  void projectErrorMapsToContainerError() throws Exception {
    var operations =
        operations(shell().on("incus list ^acme$", new ShellExec.Result(1, "", "incus down")));

    var error = assertThrows(ApiException.class, () -> operations.specs("acme"));

    assertEquals("container_error", error.error().code());
  }

  @Test
  void agentEndpointRejectsMissingProject() throws Exception {
    var operations = operations(shell().on("incus list ^acme$", EMPTY_JSON));

    var error = assertThrows(ApiException.class, () -> operations.agentStatus("acme"));

    assertEquals("project_not_created", error.error().code());
  }

  @Test
  void agentEndpointRejectsContainerErrors() throws Exception {
    var operations =
        operations(shell().on("incus list ^acme$", new ShellExec.Result(1, "", "incus down")));

    var error = assertThrows(ApiException.class, () -> operations.agentStatus("acme"));

    assertEquals("container_error", error.error().code());
  }

  @Test
  void specsRequireConfiguredAgentDirectory() throws Exception {
    var operations = operations(noAgentYaml(), shell().on("incus list ^acme$", RUNNING_JSON));

    var error = assertThrows(ApiException.class, () -> operations.specs("acme"));

    assertEquals("specs_not_configured", error.error().code());
  }

  @Test
  void agentStatusReturnsNotRunning() throws Exception {
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", new ShellExec.Result(1, "", "missing")));

    var result = operations.agentStatus("acme");

    assertEquals(false, result.get("agent_running"));
  }

  @Test
  void agentStatusReturnsRunningSessionDetails() throws Exception {
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", "123")
                .on("kill -0 123", "")
                .on(
                    "cat /home/dev/.sing/agent-session.json",
                    "{\"task\": \"work\", \"started_at\": \"2026-01-01T00:00:00Z\", \"branch\": \"sing/auth\"}"));

    var result = operations.agentStatus("acme");

    assertEquals(true, result.get("agent_running"));
    assertEquals(123, result.get("pid"));
    assertEquals("work", result.get("task"));
  }

  @Test
  void agentStatusMapsQueryFailures() throws Exception {
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .throwOn("cat /home/dev/.sing/agent.pid", new IOException("denied")));

    var error = assertThrows(ApiException.class, () -> operations.agentStatus("acme"));

    assertEquals("agent_status_failed", error.error().code());
  }

  @Test
  void agentLogHandlesMissingLog() throws Exception {
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on(
                    "tail -n 200 /home/dev/.sing/agent.log",
                    new ShellExec.Result(1, "", "No such file")));

    var result = operations.agentLog("acme", 200);

    assertEquals("No agent log found", result.get("error"));
  }

  @Test
  void agentLogReturnsLines() throws Exception {
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("tail -n 2 /home/dev/.sing/agent.log", "one\ntwo\n"));

    var result = operations.agentLog("acme", 2);

    assertEquals(List.of("one", "two"), result.get("lines"));
  }

  @Test
  void agentLogMapsThrownCommandsToApiError() throws Exception {
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .throwOn("tail -n 200 /home/dev/.sing/agent.log", new IOException("no shell")));

    var error = assertThrows(ApiException.class, () -> operations.agentLog("acme", 200));

    assertEquals("command_failed", error.error().code());
  }

  @Test
  void stopAgentReturnsNoAgentRunning() throws Exception {
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", new ShellExec.Result(1, "", "missing")));

    var result = operations.stopAgent("acme");

    assertEquals(false, result.get("stopped"));
  }

  @Test
  void dispatchLaunchesBackgroundAgent() throws Exception {
    var operations =
        operations(
            baseYaml(),
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", new ShellExec.Result(1, "", "missing"))
                .on("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML)
                .on("cat /home/dev/workspace/specs/auth/spec.md", "Do auth")
                .on("mkdir -p /home/dev/workspace/specs", "")
                .on("printf '%s'", "")
                .on("mkdir -p /home/dev/.sing", "")
                .on("claude", ""));

    var result = operations.dispatch("acme", Map.of("spec_id", "auth"));

    assertEquals(true, result.get("dispatched"));
    assertTrue(result.get("agent").toString().contains("mode=background"));
  }

  @Test
  void dispatchLaunchesForegroundAgentAndReturnsSessionDetails() throws Exception {
    var operations =
        operations(
            baseYaml(),
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", "123")
                .on("kill -0 123", new ShellExec.Result(1, "", "missing"))
                .on("cat /home/dev/.sing/agent-session.json", "{\"task\": \"work\"}")
                .on("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML)
                .on("cat /home/dev/workspace/specs/auth/spec.md", "")
                .on("mkdir -p /home/dev/workspace/specs", "")
                .on("printf '%s'", "")
                .on("mkdir -p /home/dev/.sing", "")
                .on("bash -l -c", ""));

    var result = operations.dispatch("acme", Map.of("spec_id", "auth", "mode", "foreground"));

    assertTrue(result.get("agent").toString().contains("mode=foreground"));
    assertTrue(result.get("agent").toString().contains("pid=123"));
  }

  @Test
  void dispatchMapsLaunchFailure() throws Exception {
    var operations =
        operations(
            baseYaml(),
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", new ShellExec.Result(1, "", "missing"))
                .on("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML)
                .on("cat /home/dev/workspace/specs/auth/spec.md", "Do auth")
                .on("mkdir -p /home/dev/workspace/specs", "")
                .on("printf '%s'", "")
                .on("-- mkdir -p /home/dev/.sing", "")
                .on("claude", new ShellExec.Result(1, "", "missing cli")));

    var error =
        assertThrows(
            ApiException.class, () -> operations.dispatch("acme", Map.of("spec_id", "auth")));

    assertEquals("agent_launch_failed", error.error().code());
  }

  @Test
  void dispatchLaunchesWatcherWhenGuardrailsAreConfigured() throws Exception {
    var launched = new LinkedHashMap<String, Object>();
    var operations =
        operations(
            guardrailsYaml(),
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", new ShellExec.Result(1, "", "missing"))
                .on("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML)
                .on("cat /home/dev/workspace/specs/auth/spec.md", "Do auth")
                .on("mkdir -p /home/dev/workspace/specs", "")
                .on("printf '%s'", "")
                .on("-- mkdir -p /home/dev/.sing", "")
                .on("claude", ""),
            (command, logPath) -> {
              launched.put("command", command);
              launched.put("log_path", logPath.toString());
            });

    operations.dispatch("acme", Map.of("spec_id", "auth"));

    assertTrue(launched.get("command").toString().contains("agent, watch, acme"));
    assertTrue(launched.get("log_path").toString().endsWith("watch.log"));
  }

  @Test
  void dispatchMapsWatcherFailureToLaunchFailure() throws Exception {
    var operations =
        operations(
            guardrailsYaml(),
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", new ShellExec.Result(1, "", "missing"))
                .on("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML)
                .on("cat /home/dev/workspace/specs/auth/spec.md", "Do auth")
                .on("mkdir -p /home/dev/workspace/specs", "")
                .on("printf '%s'", "")
                .on("-- mkdir -p /home/dev/.sing", "")
                .on("claude", ""),
            (command, logPath) -> {
              throw new IOException("watch failed");
            });

    var error =
        assertThrows(
            ApiException.class, () -> operations.dispatch("acme", Map.of("spec_id", "auth")));

    assertEquals("agent_launch_failed", error.error().code());
  }

  @Test
  void watcherProcessLauncherStartsCommand() throws Exception {
    var logPath = tempDir.resolve("watch.log");

    SingApiOperations.launchWatcherProcess(List.of("/bin/true"), logPath);

    assertTrue(Files.exists(logPath));
  }

  @Test
  void dispatchCreatesBranchWhenConfigured() throws Exception {
    var operations =
        operations(
            branchYaml(),
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", new ShellExec.Result(1, "", "missing"))
                .on("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML)
                .on("cat /home/dev/workspace/specs/auth/spec.md", "Do auth")
                .on("mkdir -p /home/dev/workspace/specs", "")
                .on("printf '%s'", "")
                .on("test -d /home/dev/workspace/app/.git", "")
                .on("git -C /home/dev/workspace/app checkout -b sing/auth", ""));

    var result = operations.dispatch("acme", Map.of("spec_id", "auth", "dry_run", true));

    assertEquals(true, result.get("branch_created"));
    assertTrue(result.get("spec").toString().contains("sing/auth"));
  }

  @Test
  void dispatchUsesSpecBranchWhenProvided() throws Exception {
    var operations =
        operations(
            branchYaml(),
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", new ShellExec.Result(1, "", "missing"))
                .on("cat /home/dev/workspace/specs/index.yaml", INDEX_WITH_BRANCH_YAML)
                .on("cat /home/dev/workspace/specs/auth/spec.md", "Do auth")
                .on("mkdir -p /home/dev/workspace/specs", "")
                .on("printf '%s'", "")
                .on("test -d /home/dev/workspace/app/.git", "")
                .on("git -C /home/dev/workspace/app checkout -b feat/custom", ""));

    var result = operations.dispatch("acme", Map.of("spec_id", "auth", "dry_run", true));

    assertTrue(result.get("spec").toString().contains("feat/custom"));
  }

  @Test
  void dispatchSkipsBranchWhenRepoIsMissing() throws Exception {
    var operations =
        operations(
            branchYaml(),
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", new ShellExec.Result(1, "", "missing"))
                .on("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML)
                .on("cat /home/dev/workspace/specs/auth/spec.md", "Do auth")
                .on("mkdir -p /home/dev/workspace/specs", "")
                .on("printf '%s'", "")
                .on(
                    "test -d /home/dev/workspace/app/.git",
                    new ShellExec.Result(1, "", "missing")));

    var result = operations.dispatch("acme", Map.of("spec_id", "auth", "dry_run", true));

    assertEquals(false, result.get("branch_created"));
  }

  @Test
  void dispatchMapsBranchFailure() throws Exception {
    var operations =
        operations(
            branchYaml(),
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", new ShellExec.Result(1, "", "missing"))
                .on("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML)
                .on("cat /home/dev/workspace/specs/auth/spec.md", "Do auth")
                .on("mkdir -p /home/dev/workspace/specs", "")
                .on("printf '%s'", "")
                .on("test -d /home/dev/workspace/app/.git", "")
                .on(
                    "git -C /home/dev/workspace/app checkout -b sing/auth",
                    new ShellExec.Result(1, "", "exists")));

    var error =
        assertThrows(
            ApiException.class,
            () -> operations.dispatch("acme", Map.of("spec_id", "auth", "dry_run", true)));

    assertEquals("branch_create_failed", error.error().code());
  }

  @Test
  void dispatchCreatesSnapshotWhenConfigured() throws Exception {
    var operations =
        operations(
            snapshotYaml(),
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", new ShellExec.Result(1, "", "missing"))
                .on("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML)
                .on("cat /home/dev/workspace/specs/auth/spec.md", "Do auth")
                .on("mkdir -p /home/dev/workspace/specs", "")
                .on("printf '%s'", "")
                .on("incus snapshot list acme --format json", "[]")
                .on("incus snapshot create acme", ""));

    var result = operations.dispatch("acme", Map.of("spec_id", "auth", "dry_run", true));

    assertFalse(result.get("snapshot").toString().isBlank());
  }

  @Test
  void dispatchSkipsRecentSnapshot() throws Exception {
    var snapshots = "[{\"name\": \"snap\", \"created_at\": \"" + Instant.now() + "\"}]";
    var operations =
        operations(
            snapshotYaml(),
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", new ShellExec.Result(1, "", "missing"))
                .on("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML)
                .on("cat /home/dev/workspace/specs/auth/spec.md", "Do auth")
                .on("mkdir -p /home/dev/workspace/specs", "")
                .on("printf '%s'", "")
                .on("incus snapshot list acme --format json", snapshots));

    var result = operations.dispatch("acme", Map.of("spec_id", "auth", "dry_run", true));

    assertEquals("", result.get("snapshot"));
  }

  @Test
  void dispatchCreatesSnapshotWhenLatestTimestampIsInvalid() throws Exception {
    var operations =
        operations(
            snapshotYaml(),
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", new ShellExec.Result(1, "", "missing"))
                .on("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML)
                .on("cat /home/dev/workspace/specs/auth/spec.md", "Do auth")
                .on("mkdir -p /home/dev/workspace/specs", "")
                .on("printf '%s'", "")
                .on(
                    "incus snapshot list acme --format json",
                    "[{\"name\": \"snap\", \"created_at\": \"bad\"}]")
                .on("incus snapshot create acme", ""));

    var result = operations.dispatch("acme", Map.of("spec_id", "auth", "dry_run", true));

    assertFalse(result.get("snapshot").toString().isBlank());
  }

  @Test
  void dispatchMapsSnapshotFailure() throws Exception {
    var operations =
        operations(
            snapshotYaml(),
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", new ShellExec.Result(1, "", "missing"))
                .on("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML)
                .on("cat /home/dev/workspace/specs/auth/spec.md", "Do auth")
                .on("mkdir -p /home/dev/workspace/specs", "")
                .on("printf '%s'", "")
                .on("incus snapshot list acme --format json", "[]")
                .on("incus snapshot create acme", new ShellExec.Result(1, "", "no space")));

    var error =
        assertThrows(
            ApiException.class,
            () -> operations.dispatch("acme", Map.of("spec_id", "auth", "dry_run", true)));

    assertEquals("snapshot_failed", error.error().code());
  }

  @Test
  void stopAgentKillsRunningAgent() throws Exception {
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", "123")
                .on("kill -0 123", "")
                .on("cat /home/dev/.sing/agent-session.json", "{\"task\": \"work\"}")
                .on("kill 123", "")
                .on("sleep 3", "")
                .on("kill -9 123", "")
                .on("rm -f /home/dev/.sing/agent.pid", ""));

    var result = operations.stopAgent("acme");

    assertEquals(true, result.get("stopped"));
    assertEquals(123, result.get("pid"));
  }

  @Test
  void stopAgentMapsKillFailure() throws Exception {
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", "123")
                .on("kill -0 123", "")
                .on("cat /home/dev/.sing/agent-session.json", "{\"task\": \"work\"}")
                .throwOn("kill 123", new IOException("permission denied")));

    var error = assertThrows(ApiException.class, () -> operations.stopAgent("acme"));

    assertEquals("agent_stop_failed", error.error().code());
  }

  @Test
  void agentReportReturnsSummary() throws Exception {
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", new ShellExec.Result(1, "", "missing"))
                .on("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML));

    var result = operations.agentReport("acme");

    assertEquals("acme", result.get("name"));
    assertEquals("No session", result.get("session_status"));
  }

  @Test
  void agentReportMapsReporterFailure() throws Exception {
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .throwOn("cat /home/dev/.sing/agent.pid", new IOException("boom")));

    var error = assertThrows(ApiException.class, () -> operations.agentReport("acme"));

    assertEquals("agent_report_failed", error.error().code());
  }

  @Test
  void agentLogFailureMapsToApiError() throws Exception {
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on(
                    "tail -n 200 /home/dev/.sing/agent.log",
                    new ShellExec.Result(1, "", "permission denied")));

    var error = assertThrows(ApiException.class, () -> operations.agentLog("acme", 200));

    assertEquals("agent_log_failed", error.error().code());
  }

  @Test
  void missingDescriptorMapsToNotFound() {
    var operations = new SingApiOperations(shell(), tempDir.resolve("missing.yaml").toString());

    var error = assertThrows(ApiException.class, () -> operations.project("acme"));

    assertEquals("project_descriptor_not_found", error.error().code());
  }

  @Test
  void malformedDescriptorMapsToProjectLoadFailure() throws Exception {
    var operations = operations("name: [", shell().on("incus list ^acme$", RUNNING_JSON));

    var error = assertThrows(ApiException.class, () -> operations.project("acme"));

    assertEquals("project_load_failed", error.error().code());
  }

  @Test
  void specIndexFailuresMapToApiErrors() throws Exception {
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on(
                    "cat /home/dev/workspace/specs/index.yaml",
                    new ShellExec.Result(1, "", "denied")));

    var error = assertThrows(ApiException.class, () -> operations.specs("acme"));

    assertEquals("specs_read_failed", error.error().code());
  }

  @Test
  void specBodyFailuresMapToApiErrors() throws Exception {
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML)
                .throwOn("cat /home/dev/workspace/specs/auth/spec.md", new IOException("denied")));

    var error = assertThrows(ApiException.class, () -> operations.spec("acme", "auth"));

    assertEquals("spec_read_failed", error.error().code());
  }

  @Test
  void statusUpdateFailuresMapToApiErrors() throws Exception {
    var operations =
        operations(
            shell()
                .on("incus list ^acme$", RUNNING_JSON)
                .on("cat /home/dev/.sing/agent.pid", new ShellExec.Result(1, "", "missing"))
                .on("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML)
                .throwOn("mkdir -p /home/dev/workspace/specs", new IOException("denied")));

    var error =
        assertThrows(
            ApiException.class, () -> operations.dispatch("acme", Map.of("spec_id", "auth")));

    assertEquals("spec_status_update_failed", error.error().code());
  }

  private SingApiOperations operations(String yamlContent, FakeShell shell) throws Exception {
    var yaml = tempDir.resolve("sing-" + System.nanoTime() + ".yaml");
    Files.writeString(yaml, yamlContent);
    return new SingApiOperations(shell, yaml.toString());
  }

  private SingApiOperations operations(
      String yamlContent, FakeShell shell, SingApiOperations.WatcherLauncher watcherLauncher)
      throws Exception {
    var yaml = tempDir.resolve("sing-" + System.nanoTime() + ".yaml");
    Files.writeString(yaml, yamlContent);
    return new SingApiOperations(shell, yaml.toString(), watcherLauncher);
  }

  private static String baseYaml() {
    return """
        name: acme
        ssh:
          user: dev
        agent:
          type: claude-code
          specs_dir: specs
        """;
  }

  private static String noAgentYaml() {
    return """
        name: acme
        ssh:
          user: dev
        """;
  }

  private static String branchYaml() {
    return """
        name: acme
        ssh:
          user: dev
        repos:
          - url: https://github.com/acme/app.git
            path: app
        agent:
          type: claude-code
          specs_dir: specs
          auto_branch: true
          branch_prefix: sing/
        """;
  }

  private static String snapshotYaml() {
    return """
        name: acme
        ssh:
          user: dev
        agent:
          type: claude-code
          specs_dir: specs
          auto_snapshot: true
        """;
  }

  private static String guardrailsYaml() {
    return """
        name: acme
        ssh:
          user: dev
        agent:
          type: claude-code
          specs_dir: specs
          guardrails:
            max_duration: 4h
            action: stop
        """;
  }

  private SingApiOperations operations(FakeShell shell) throws Exception {
    var yaml = tempDir.resolve("sing.yaml");
    Files.writeString(yaml, baseYaml());
    return new SingApiOperations(shell, yaml.toString());
  }

  private static FakeShell shell() {
    return new FakeShell();
  }

  private static final class FakeShell implements ShellExec {
    private final Map<String, Result> scripts = new LinkedHashMap<>();
    private final Map<String, Exception> failures = new LinkedHashMap<>();

    FakeShell on(String pattern, String stdout) {
      return on(pattern, new Result(0, stdout, ""));
    }

    FakeShell on(String pattern, Result result) {
      scripts.put(pattern, result);
      return this;
    }

    FakeShell throwOn(String pattern, Exception failure) {
      failures.put(pattern, failure);
      return this;
    }

    @Override
    public Result exec(List<String> command) throws IOException {
      var joined = String.join(" ", command);
      for (var entry : failures.entrySet()) {
        if (joined.contains(entry.getKey())) {
          throw (IOException) entry.getValue();
        }
      }
      for (var entry : scripts.entrySet()) {
        if (joined.contains(entry.getKey())) {
          return entry.getValue();
        }
      }
      return new Result(1, "", "no script for " + joined);
    }

    @Override
    public Result exec(List<String> command, Path workDir, Duration timeout) throws IOException {
      return exec(command);
    }

    @Override
    public boolean isDryRun() {
      return false;
    }
  }
}
