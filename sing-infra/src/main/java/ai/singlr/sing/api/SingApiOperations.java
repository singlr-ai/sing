/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.api;

import ai.singlr.sing.config.SingYaml;
import ai.singlr.sing.config.Spec;
import ai.singlr.sing.config.SpecDirectory;
import ai.singlr.sing.config.YamlUtil;
import ai.singlr.sing.engine.AgentCli;
import ai.singlr.sing.engine.AgentReporter;
import ai.singlr.sing.engine.AgentSession;
import ai.singlr.sing.engine.ContainerExec;
import ai.singlr.sing.engine.ContainerManager;
import ai.singlr.sing.engine.ContainerState;
import ai.singlr.sing.engine.ShellExec;
import ai.singlr.sing.engine.ShellExecutor;
import ai.singlr.sing.engine.SingPaths;
import ai.singlr.sing.engine.SnapshotManager;
import ai.singlr.sing.engine.SpecWorkspace;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SingApiOperations implements ApiOperations {

  private static final Duration SNAPSHOT_INTERVAL = Duration.ofHours(24);

  private final ShellExec shell;
  private final String file;

  public SingApiOperations() {
    this(new ShellExecutor(false), "sing.yaml");
  }

  public SingApiOperations(ShellExec shell, String file) {
    this.shell = shell;
    this.file = file;
  }

  @Override
  public Map<String, Object> health() {
    var map = new LinkedHashMap<String, Object>();
    map.put("status", "ok");
    return map;
  }

  @Override
  public Map<String, Object> project(String project) {
    var loaded = loadProject(project);
    var map = new LinkedHashMap<String, Object>();
    map.put("name", project);
    map.put("container_status", statusName(loaded.state()));
    if (loaded.config().agent() != null) {
      var agent = new LinkedHashMap<String, Object>();
      agent.put("type", loaded.config().agent().type());
      agent.put("auto_snapshot", loaded.config().agent().autoSnapshot());
      agent.put("auto_branch", loaded.config().agent().autoBranch());
      agent.put("specs_dir", loaded.config().agent().specsDir());
      map.put("agent", agent);
    }
    return map;
  }

  @Override
  public Map<String, Object> specs(String project) {
    var loaded = loadRunningProject(project);
    var specs = readIndex(workspace(loaded));
    var summary = SpecDirectory.summarize(specs);
    var map = new LinkedHashMap<String, Object>();
    map.put("name", project);
    map.put("specs", specs.stream().map(spec -> specMap(specs, spec)).toList());
    map.put("counts", summary.counts());
    map.put("summary", summary.toMap());
    return map;
  }

  @Override
  public Map<String, Object> spec(String project, String specId) {
    var loaded = loadRunningProject(project);
    var workspace = workspace(loaded);
    var specs = readIndex(workspace);
    var spec = SpecDirectory.findById(specs, specId);
    if (spec == null) {
      throw new ApiException(404, "spec_not_found", "Spec '" + specId + "' was not found.", null);
    }
    var content = readSpecBody(workspace, specId);
    var map = new LinkedHashMap<String, Object>();
    map.put("name", project);
    map.put("spec", specMap(specs, spec));
    map.put("spec_path", workspace.specMarkdownPath(specId));
    map.put("content_available", content != null);
    if (content != null) {
      map.put("content", content);
    }
    return map;
  }

  @Override
  public Map<String, Object> dispatch(String project, Map<String, Object> request) {
    var loaded = loadRunningProject(project);
    var specId = optionalString(request, "spec_id");
    var mode = Objects.toString(request.getOrDefault("mode", "background"));
    var dryRun = Boolean.TRUE.equals(request.get("dry_run"));
    if (!mode.equals("background") && !mode.equals("foreground")) {
      throw new ApiException(
          422, "invalid_mode", "Dispatch mode must be background or foreground.", null);
    }

    var agentSession = new AgentSession(shell);
    var existing = querySession(agentSession, project);
    if (existing != null && existing.running()) {
      throw new ApiException(
          409,
          "agent_already_running",
          "Agent is already running for project '" + project + "'.",
          "Stop the active agent before dispatching another spec.");
    }

    var workspace = workspace(loaded);
    var specs = readIndex(workspace);
    var nextSpec = resolveSpec(specs, specId);
    if (nextSpec == null) {
      var map = new LinkedHashMap<String, Object>();
      map.put("name", project);
      map.put("dispatched", false);
      map.put("reason", "no_pending_specs");
      return map;
    }

    updateStatus(workspace, nextSpec.id(), "in_progress");
    var specBody = Objects.requireNonNullElse(readSpecBody(workspace, nextSpec.id()), "");
    var task = buildTaskPrompt(nextSpec, specBody.isBlank() ? nextSpec.title() : specBody);
    var branch = branchName(loaded.config(), nextSpec);
    var snapshot = createSnapshotIfNeeded(project, loaded.config());
    var branchCreated = createBranchIfNeeded(project, loaded.config(), branch);

    if (!dryRun) {
      launchAgent(project, loaded.config(), task, branch, mode);
    }

    var status = dryRun ? null : querySession(agentSession, project);
    var map = new LinkedHashMap<String, Object>();
    map.put("name", project);
    map.put("dispatched", true);
    map.put("spec", dispatchedSpecMap(nextSpec, branch));
    map.put("agent", agentMap(loaded.config(), mode, status));
    map.put("snapshot", snapshot);
    map.put("branch_created", branchCreated);
    return map;
  }

  @Override
  public Map<String, Object> agentStatus(String project) {
    requireProjectExists(project);
    var info = querySession(new AgentSession(shell), project);
    var map = new LinkedHashMap<String, Object>();
    map.put("name", project);
    map.put("agent_running", info != null && info.running());
    if (info != null) {
      map.putAll(sessionMap(info));
    }
    return map;
  }

  @Override
  public Map<String, Object> agentLog(String project, int tail) {
    requireProjectExists(project);
    var cmd =
        ContainerExec.asDevUser(
            project, List.of("tail", "-n", String.valueOf(tail), AgentSession.logPath()));
    var result = exec(cmd);
    var map = new LinkedHashMap<String, Object>();
    map.put("name", project);
    if (!result.ok()) {
      if (result.stderr().contains("No such file")) {
        map.put("lines", List.of());
        map.put("error", "No agent log found");
        return map;
      }
      throw new ApiException(500, "agent_log_failed", "Failed to read agent log.", null);
    }
    map.put(
        "lines",
        Arrays.stream(result.stdout().split("\n")).filter(line -> !line.isEmpty()).toList());
    return map;
  }

  @Override
  public Map<String, Object> stopAgent(String project) {
    requireProjectExists(project);
    var agentSession = new AgentSession(shell);
    var info = querySession(agentSession, project);
    var map = new LinkedHashMap<String, Object>();
    map.put("name", project);
    if (info == null || !info.running()) {
      map.put("stopped", false);
      map.put("reason", "no_agent_running");
      return map;
    }
    try {
      agentSession.killAgent(project);
    } catch (Exception e) {
      throw new ApiException(500, "agent_stop_failed", "Failed to stop agent.", null);
    }
    map.put("stopped", true);
    map.put("pid", info.pid());
    return map;
  }

  @Override
  public Map<String, Object> agentReport(String project) {
    var loaded = loadProject(project);
    try {
      return new AgentReporter(shell).generate(project, loaded.config()).toMap();
    } catch (Exception e) {
      throw new ApiException(500, "agent_report_failed", "Failed to generate agent report.", null);
    }
  }

  private static List<Spec> readIndex(SpecWorkspace workspace) {
    try {
      return workspace.readIndex();
    } catch (Exception e) {
      throw new ApiException(500, "specs_read_failed", "Failed to read specs index.", null);
    }
  }

  private static String readSpecBody(SpecWorkspace workspace, String specId) {
    try {
      return workspace.readSpecBody(specId);
    } catch (Exception e) {
      throw new ApiException(500, "spec_read_failed", "Failed to read spec content.", null);
    }
  }

  private static void updateStatus(SpecWorkspace workspace, String specId, String status) {
    try {
      workspace.updateStatus(specId, status);
    } catch (Exception e) {
      throw new ApiException(
          500, "spec_status_update_failed", "Failed to update spec status.", null);
    }
  }

  private static String statusName(ContainerState state) {
    return switch (state) {
      case ContainerState.Running ignored -> "running";
      case ContainerState.Stopped ignored -> "stopped";
      case ContainerState.NotCreated ignored -> "not_created";
      case ContainerState.Error ignored -> "error";
    };
  }

  private LoadedProject loadRunningProject(String project) {
    var loaded = loadProject(project);
    switch (loaded.state()) {
      case ContainerState.Running ignored -> {
        return loaded;
      }
      case ContainerState.Stopped ignored ->
          throw new ApiException(
              409,
              "project_stopped",
              "Project '" + project + "' is stopped.",
              "Start it with sing up " + project + ".");
      case ContainerState.NotCreated ignored ->
          throw new ApiException(
              404, "project_not_created", "Project '" + project + "' does not exist.", null);
      case ContainerState.Error e ->
          throw new ApiException(500, "container_error", e.message(), null);
    }
  }

  private LoadedProject loadProject(String project) {
    var singYamlPath = SingPaths.resolveSingYaml(project, file);
    if (!Files.exists(singYamlPath)) {
      throw new ApiException(
          404,
          "project_descriptor_not_found",
          "Project descriptor was not found: " + singYamlPath.toAbsolutePath(),
          null);
    }
    try {
      var config = SingYaml.fromMap(YamlUtil.parseFile(singYamlPath));
      var state = new ContainerManager(shell).queryState(project);
      return new LoadedProject(config, state);
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(500, "project_load_failed", "Failed to load project.", null);
    }
  }

  private void requireProjectExists(String project) {
    var state = loadProject(project).state();
    if (state instanceof ContainerState.NotCreated) {
      throw new ApiException(
          404, "project_not_created", "Project '" + project + "' does not exist.", null);
    }
    if (state instanceof ContainerState.Error e) {
      throw new ApiException(500, "container_error", e.message(), null);
    }
  }

  private SpecWorkspace workspace(LoadedProject loaded) {
    if (loaded.config().agent() == null || loaded.config().agent().specsDir() == null) {
      throw new ApiException(
          422,
          "specs_not_configured",
          "No specs_dir configured in the project agent block.",
          "Add specs_dir to sing.yaml.");
    }
    var specsDir =
        "/home/" + loaded.config().sshUser() + "/workspace/" + loaded.config().agent().specsDir();
    return new SpecWorkspace(shell, loaded.config().name(), specsDir);
  }

  private static Spec resolveSpec(List<Spec> specs, String specId) {
    if (specId == null || specId.isBlank()) {
      return SpecDirectory.nextReady(specs);
    }
    var spec = SpecDirectory.findById(specs, specId);
    if (spec == null) {
      throw new ApiException(404, "spec_not_found", "Spec '" + specId + "' was not found.", null);
    }
    if (!SpecDirectory.isReady(specs, spec)) {
      throw new ApiException(
          409,
          "spec_not_ready",
          "Spec '" + specId + "' is not ready for dispatch.",
          "Resolve dependencies or choose a ready spec.");
    }
    return spec;
  }

  private static Map<String, Object> specMap(List<Spec> specs, Spec spec) {
    var map = new LinkedHashMap<String, Object>(spec.toMap());
    map.put("ready", SpecDirectory.isReady(specs, spec));
    map.put("blocked", SpecDirectory.isBlocked(specs, spec));
    var unmet = SpecDirectory.unmetDependencies(specs, spec);
    if (!unmet.isEmpty()) {
      map.put("unmet_dependencies", unmet);
    }
    return map;
  }

  private static Map<String, Object> dispatchedSpecMap(Spec spec, String branch) {
    var map = new LinkedHashMap<String, Object>();
    map.put("id", spec.id());
    map.put("title", spec.title());
    map.put("status", "in_progress");
    if (branch != null && !branch.isBlank()) {
      map.put("branch", branch);
    }
    return map;
  }

  private static String buildTaskPrompt(Spec spec, String description) {
    return "Your current spec: \"" + spec.title() + "\" (id: " + spec.id() + ").\n\n" + description;
  }

  private static String branchName(SingYaml config, Spec spec) {
    if (config.agent() == null || !config.agent().autoBranch()) {
      return "";
    }
    var prefix = config.agent().branchPrefix() != null ? config.agent().branchPrefix() : "sing/";
    return spec.branch() != null ? spec.branch() : prefix + spec.id();
  }

  private String createSnapshotIfNeeded(String project, SingYaml config) {
    if (config.agent() == null || !config.agent().autoSnapshot()) {
      return "";
    }
    var snapMgr = new SnapshotManager(shell);
    if (!shouldSnapshot(snapMgr, project)) {
      return "";
    }
    var label = SnapshotManager.defaultLabel();
    try {
      snapMgr.create(project, label);
      return label;
    } catch (Exception e) {
      throw new ApiException(500, "snapshot_failed", "Failed to create dispatch snapshot.", null);
    }
  }

  private boolean createBranchIfNeeded(String project, SingYaml config, String branch) {
    if (branch == null
        || branch.isBlank()
        || config.repos() == null
        || config.repos().size() != 1) {
      return false;
    }
    var repoDir = "/home/" + config.sshUser() + "/workspace/" + config.repos().getFirst().path();
    var repoExists =
        exec(ContainerExec.asDevUser(project, List.of("test", "-d", repoDir + "/.git")));
    if (!repoExists.ok()) {
      return false;
    }
    var result =
        exec(
            ContainerExec.asDevUser(
                project, List.of("git", "-C", repoDir, "checkout", "-b", branch)));
    if (!result.ok()) {
      throw new ApiException(
          500, "branch_create_failed", "Failed to create branch '" + branch + "'.", null);
    }
    return true;
  }

  private void launchAgent(
      String project, SingYaml config, String task, String branch, String mode) {
    try {
      var session = new AgentSession(shell);
      session.ensureDirectory(project);
      session.writeTaskFile(project, task);
      session.writeSession(project, task, Objects.requireNonNullElse(branch, ""));
      var agentCli = AgentCli.fromYamlName(config.agent().type());
      var workDir = "/home/" + config.sshUser() + "/workspace";
      var command =
          mode.equals("background")
              ? AgentSession.buildBackgroundLaunchCommand(
                  project, config.sshUser(), workDir, true, agentCli)
              : AgentSession.buildForegroundTaskCommand(
                  project, config.sshUser(), workDir, true, agentCli);
      var result = exec(command);
      if (!result.ok()) {
        throw new ApiException(500, "agent_launch_failed", "Failed to launch agent.", null);
      }
      launchWatcherIfGuardrails(project, config);
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(500, "agent_launch_failed", "Failed to launch agent.", null);
    }
  }

  private void launchWatcherIfGuardrails(String project, SingYaml config) throws IOException {
    if (config.agent() == null || config.agent().guardrails() == null) {
      return;
    }
    var cmd =
        List.of(
            "nohup",
            SingPaths.binaryPath().toString(),
            "agent",
            "watch",
            project,
            "-f",
            SingPaths.resolveSingYaml(project, file).toAbsolutePath().toString());
    var watchLog = SingPaths.projectDir(project).resolve("watch.log");
    Files.createDirectories(watchLog.getParent());
    new ProcessBuilder(cmd)
        .redirectOutput(ProcessBuilder.Redirect.to(watchLog.toFile()))
        .redirectErrorStream(true)
        .start();
  }

  private static boolean shouldSnapshot(SnapshotManager snapMgr, String project) {
    try {
      var snapshots = snapMgr.list(project);
      if (snapshots.isEmpty()) {
        return true;
      }
      var latestTime = OffsetDateTime.parse(snapshots.getLast().createdAt()).toInstant();
      return Instant.now().isAfter(latestTime.plus(SNAPSHOT_INTERVAL));
    } catch (Exception ignored) {
      return true;
    }
  }

  private AgentSession.SessionInfo querySession(AgentSession session, String project) {
    try {
      return session.queryStatus(project);
    } catch (Exception e) {
      throw new ApiException(500, "agent_status_failed", "Failed to query agent status.", null);
    }
  }

  private Map<String, Object> agentMap(
      SingYaml config, String mode, AgentSession.SessionInfo info) {
    var map = new LinkedHashMap<String, Object>();
    map.put("type", config.agent() != null ? config.agent().type() : "");
    map.put("mode", mode);
    map.put("running", info != null && info.running());
    if (info != null) {
      map.putAll(sessionMap(info));
    }
    return map;
  }

  private static Map<String, Object> sessionMap(AgentSession.SessionInfo info) {
    var map = new LinkedHashMap<String, Object>();
    map.put("pid", info.pid());
    map.put("task", info.task());
    map.put("started_at", info.startedAt());
    map.put("branch", info.branch());
    map.put("log_path", info.logPath());
    return map;
  }

  private ShellExec.Result exec(List<String> command) {
    try {
      return shell.exec(command);
    } catch (Exception e) {
      throw new ApiException(500, "command_failed", "A sing system command failed.", null);
    }
  }

  private static String optionalString(Map<String, Object> map, String key) {
    var value = map.get(key);
    return value == null ? null : value.toString();
  }

  private record LoadedProject(SingYaml config, ContainerState state) {}
}
