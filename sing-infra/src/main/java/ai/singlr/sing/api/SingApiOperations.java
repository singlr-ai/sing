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
import ai.singlr.sing.engine.GitSpecSync;
import ai.singlr.sing.engine.NameValidator;
import ai.singlr.sing.engine.ShellExec;
import ai.singlr.sing.engine.ShellExecutor;
import ai.singlr.sing.engine.SingPaths;
import ai.singlr.sing.engine.SnapshotManager;
import ai.singlr.sing.engine.SpecWorkspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class SingApiOperations implements ApiOperations {

  private static final Duration SNAPSHOT_INTERVAL = Duration.ofHours(24);

  private final ShellExec shell;
  private final String file;
  private final WatcherLauncher watcherLauncher;

  public SingApiOperations() {
    this(new ShellExecutor(false), "sing.yaml");
  }

  public SingApiOperations(ShellExec shell, String file) {
    this(shell, file, SingApiOperations::launchWatcherProcess);
  }

  SingApiOperations(ShellExec shell, String file, WatcherLauncher watcherLauncher) {
    this.shell = shell;
    this.file = file;
    this.watcherLauncher = watcherLauncher;
  }

  @Override
  public Result<HealthResponse> health() {
    return Result.success(new HealthResponse("ok"));
  }

  @Override
  public Result<ProjectResponse> project(String project) {
    return safe(() -> projectValue(project));
  }

  @Override
  public Result<SpecsResponse> specs(String project) {
    return safe(() -> specsValue(project));
  }

  @Override
  public Result<SpecResponse> spec(String project, String specId) {
    return safe(() -> specValue(project, specId));
  }

  @Override
  public Result<SpecSyncResponse> specSyncStatus(String project) {
    return safe(() -> specSyncStatusValue(project));
  }

  @Override
  public Result<SpecSyncResponse> specSync(String project, SpecSyncRequest request) {
    return safe(() -> specSyncValue(project, request));
  }

  @Override
  public Result<DispatchResponse> dispatch(String project, DispatchRequest request) {
    return safe(() -> dispatchValue(project, request));
  }

  @Override
  public Result<AgentStatusResponse> agentStatus(String project) {
    return safe(() -> agentStatusValue(project));
  }

  @Override
  public Result<AgentLogResponse> agentLog(String project, int tail) {
    return safe(() -> agentLogValue(project, tail));
  }

  @Override
  public Result<StopAgentResponse> stopAgent(String project) {
    return safe(() -> stopAgentValue(project));
  }

  @Override
  public Result<AgentReportResponse> agentReport(String project) {
    return safe(() -> agentReportValue(project));
  }

  private ProjectResponse projectValue(String project) {
    var loaded = loadProject(project);
    var agent = loaded.config().agent() != null ? agentConfigView(loaded.config()) : null;
    return new ProjectResponse(project, statusName(loaded.state()), agent);
  }

  private SpecsResponse specsValue(String project) {
    var loaded = loadRunningProject(project);
    var specs = readSpecs(workspace(loaded));
    var summary = SpecDirectory.summarize(specs);
    return new SpecsResponse(
        project,
        specs.stream().map(spec -> specView(specs, spec)).toList(),
        summaryView(summary.counts()),
        boardSummaryView(summary));
  }

  private SpecResponse specValue(String project, String specId) {
    var loaded = loadRunningProject(project);
    var workspace = workspace(loaded);
    var specs = readSpecs(workspace);
    var spec = SpecDirectory.findById(specs, specId);
    if (spec == null) {
      throw new ApiException(ErrorCode.SPEC_NOT_FOUND, "Spec '" + specId + "' was not found.");
    }
    var content = readSpecBody(workspace, specId);
    return new SpecResponse(
        project,
        specView(specs, spec),
        workspace.specMarkdownPath(specId),
        content != null,
        content);
  }

  private SpecSyncResponse specSyncStatusValue(String project) {
    var loaded = loadRunningProject(project);
    try {
      return specSyncResponse(project, specSync(loaded).status());
    } catch (Exception e) {
      throw new ApiException(ErrorCode.SPEC_SYNC_FAILED, "Failed to read spec sync status.", e);
    }
  }

  private SpecSyncResponse specSyncValue(String project, SpecSyncRequest request) {
    var loaded = loadRunningProject(project);
    NameValidator.requireValidGitRef(request.branch(), "branch");
    try {
      var sync = specSync(loaded);
      return switch (request.operation().toLowerCase()) {
        case "status" -> specSyncResponse(project, sync.status());
        case "pull" -> specSyncResponse(project, sync.pull());
        case "push" -> specSyncResponse(project, sync.push());
        case "init" -> specSyncResponse(project, sync.init(request.remote(), request.branch()));
        default ->
            throw new ApiException(
                ErrorCode.INVALID_REQUEST,
                "Unknown spec sync operation: " + request.operation() + ".",
                "Use status, pull, push, or init.");
      };
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ApiException(ErrorCode.SPEC_SYNC_FAILED, "Failed to synchronize specs.", e);
    }
  }

  private DispatchResponse dispatchValue(String project, DispatchRequest request) {
    var loaded = loadRunningProject(project);
    if (!request.mode().equals("background") && !request.mode().equals("foreground")) {
      throw new ApiException(
          ErrorCode.INVALID_MODE, "Dispatch mode must be background or foreground.");
    }

    var agentSession = new AgentSession(shell);
    var existing = querySession(agentSession, project);
    if (existing != null && existing.running()) {
      throw new ApiException(
          ErrorCode.AGENT_ALREADY_RUNNING,
          "Agent is already running for project '" + project + "'.",
          "Stop the active agent before dispatching another spec.");
    }

    var workspace = workspace(loaded);
    var specs = readSpecs(workspace);
    var nextSpec = resolveSpec(specs, request.specId());
    if (nextSpec == null) {
      return new DispatchResponse(project, false, "no_pending_specs", null, null, "", false);
    }

    updateStatus(workspace, nextSpec.id(), "in_progress");
    var specBody = Objects.requireNonNullElse(readSpecBody(workspace, nextSpec.id()), "");
    var task = buildTaskPrompt(nextSpec, specBody.isBlank() ? nextSpec.title() : specBody);
    var branch = branchName(loaded.config(), nextSpec);
    var snapshot = createSnapshotIfNeeded(project, loaded.config());
    var branchCreated = createBranchIfNeeded(project, loaded.config(), branch);

    if (!request.dryRun()) {
      launchAgent(project, loaded.config(), task, branch, request.mode());
    }

    var status = request.dryRun() ? null : querySession(agentSession, project);
    return new DispatchResponse(
        project,
        true,
        null,
        dispatchedSpecView(nextSpec, branch),
        agentStatusView(loaded.config(), request.mode(), status),
        snapshot,
        branchCreated);
  }

  private AgentStatusResponse agentStatusValue(String project) {
    requireProjectExists(project);
    var info = querySession(new AgentSession(shell), project);
    return new AgentStatusResponse(
        project,
        info != null && info.running(),
        info != null ? info.pid() : null,
        info != null ? info.task() : null,
        info != null ? info.startedAt() : null,
        info != null ? info.branch() : null,
        info != null ? info.logPath() : null);
  }

  private AgentLogResponse agentLogValue(String project, int tail) {
    requireProjectExists(project);
    var cmd =
        ContainerExec.asDevUser(
            project, List.of("tail", "-n", String.valueOf(tail), AgentSession.logPath()));
    var result = exec(cmd);
    if (!result.ok()) {
      if (result.stderr().contains("No such file")) {
        return new AgentLogResponse(project, List.of(), "No agent log found");
      }
      throw new ApiException(ErrorCode.AGENT_LOG_FAILED, "Failed to read agent log.");
    }
    var lines = Arrays.stream(result.stdout().split("\n")).filter(line -> !line.isEmpty()).toList();
    return new AgentLogResponse(project, lines, null);
  }

  private StopAgentResponse stopAgentValue(String project) {
    requireProjectExists(project);
    var agentSession = new AgentSession(shell);
    var info = querySession(agentSession, project);
    if (info == null || !info.running()) {
      return new StopAgentResponse(project, false, "no_agent_running", null);
    }
    try {
      agentSession.killAgent(project);
    } catch (Exception e) {
      throw new ApiException(ErrorCode.AGENT_STOP_FAILED, "Failed to stop agent.", e);
    }
    return new StopAgentResponse(project, true, null, info.pid());
  }

  private AgentReportResponse agentReportValue(String project) {
    var loaded = loadProject(project);
    try {
      return agentReportView(new AgentReporter(shell).generate(project, loaded.config()));
    } catch (Exception e) {
      throw new ApiException(ErrorCode.AGENT_REPORT_FAILED, "Failed to generate agent report.", e);
    }
  }

  private static List<Spec> readSpecs(SpecWorkspace workspace) {
    try {
      return workspace.readSpecs();
    } catch (Exception e) {
      throw new ApiException(ErrorCode.SPECS_READ_FAILED, "Failed to read spec metadata.", e);
    }
  }

  private static String readSpecBody(SpecWorkspace workspace, String specId) {
    try {
      return workspace.readSpecBody(specId);
    } catch (Exception e) {
      throw new ApiException(ErrorCode.SPEC_READ_FAILED, "Failed to read spec content.", e);
    }
  }

  private static void updateStatus(SpecWorkspace workspace, String specId, String status) {
    try {
      workspace.updateStatus(specId, status);
    } catch (Exception e) {
      throw new ApiException(
          ErrorCode.SPEC_STATUS_UPDATE_FAILED, "Failed to update spec status.", e);
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
              ErrorCode.PROJECT_STOPPED,
              "Project '" + project + "' is stopped.",
              "Start it with sing project start " + project + ".");
      case ContainerState.NotCreated ignored ->
          throw new ApiException(
              ErrorCode.PROJECT_NOT_CREATED, "Project '" + project + "' does not exist.");
      case ContainerState.Error error ->
          throw new ApiException(ErrorCode.CONTAINER_ERROR, error.message());
    }
  }

  private LoadedProject loadProject(String project) {
    var singYamlPath = SingPaths.resolveSingYaml(project, file);
    if (!Files.exists(singYamlPath)) {
      throw new ApiException(
          ErrorCode.PROJECT_DESCRIPTOR_NOT_FOUND,
          "Project descriptor was not found: " + singYamlPath.toAbsolutePath());
    }
    try {
      var config = SingYaml.fromMap(YamlUtil.parseFile(singYamlPath));
      var state = new ContainerManager(shell).queryState(project);
      return new LoadedProject(config, state);
    } catch (Exception e) {
      throw new ApiException(ErrorCode.PROJECT_LOAD_FAILED, "Failed to load project.", e);
    }
  }

  private void requireProjectExists(String project) {
    var state = loadProject(project).state();
    if (state instanceof ContainerState.NotCreated) {
      throw new ApiException(
          ErrorCode.PROJECT_NOT_CREATED, "Project '" + project + "' does not exist.");
    }
    if (state instanceof ContainerState.Error error) {
      throw new ApiException(ErrorCode.CONTAINER_ERROR, error.message());
    }
  }

  private GitSpecSync specSync(LoadedProject loaded) {
    var specsDir = specsDir(loaded.config());
    return new GitSpecSync(
        shell, ContainerExec.asDevUser(loaded.config().name(), List.of("git", "-C", specsDir)));
  }

  private SpecWorkspace workspace(LoadedProject loaded) {
    return new SpecWorkspace(shell, loaded.config().name(), specsDir(loaded.config()));
  }

  private static String specsDir(SingYaml config) {
    if (config.agent() == null || config.agent().specsDir() == null) {
      throw new ApiException(
          ErrorCode.SPECS_NOT_CONFIGURED,
          "No specs_dir configured in the project agent block.",
          "Add specs_dir to sing.yaml.");
    }
    NameValidator.requireSafePath(config.agent().specsDir(), "agent.specs_dir");
    NameValidator.requireValidSshUser(config.sshUser());
    return "/home/" + config.sshUser() + "/workspace/" + config.agent().specsDir();
  }

  private static Spec resolveSpec(List<Spec> specs, String specId) {
    if (specId == null || specId.isBlank()) {
      return SpecDirectory.nextReady(specs);
    }
    var spec = SpecDirectory.findById(specs, specId);
    if (spec == null) {
      throw new ApiException(ErrorCode.SPEC_NOT_FOUND, "Spec '" + specId + "' was not found.");
    }
    if (!SpecDirectory.isReady(specs, spec)) {
      throw new ApiException(
          ErrorCode.SPEC_NOT_READY,
          "Spec '" + specId + "' is not ready for dispatch.",
          "Resolve dependencies or choose a ready spec.");
    }
    return spec;
  }

  private static SpecView specView(List<Spec> specs, Spec spec) {
    return new SpecView(
        spec.id(),
        spec.title(),
        spec.status(),
        spec.assignee(),
        spec.dependsOn(),
        spec.branch(),
        SpecDirectory.isReady(specs, spec),
        SpecDirectory.isBlocked(specs, spec),
        SpecDirectory.unmetDependencies(specs, spec));
  }

  private static SpecSyncResponse specSyncResponse(String project, GitSpecSync.Status status) {
    return new SpecSyncResponse(
        project, null, false, status.message(), specSyncStatusView(status), null);
  }

  private static SpecSyncResponse specSyncResponse(
      String project, GitSpecSync.OperationResult result) {
    return new SpecSyncResponse(
        project,
        result.operation(),
        result.changed(),
        result.message(),
        specSyncStatusView(result.after()),
        specSyncStatusView(result.before()));
  }

  private static SpecSyncStatusView specSyncStatusView(GitSpecSync.Status status) {
    return new SpecSyncStatusView(
        status.state(),
        status.branch(),
        status.upstream(),
        status.ahead(),
        status.behind(),
        status.dirty(),
        status.conflicted(),
        status.repository(),
        status.message());
  }

  private static DispatchedSpecView dispatchedSpecView(Spec spec, String branch) {
    return new DispatchedSpecView(
        spec.id(),
        spec.title(),
        "in_progress",
        branch != null && !branch.isBlank() ? branch : null);
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
      throw new ApiException(ErrorCode.SNAPSHOT_FAILED, "Failed to create dispatch snapshot.", e);
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
          ErrorCode.BRANCH_CREATE_FAILED, "Failed to create branch '" + branch + "'.");
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
        throw new ApiException(ErrorCode.AGENT_LAUNCH_FAILED, "Failed to launch agent.");
      }
      launchWatcherIfGuardrails(project, config);
    } catch (Exception e) {
      throw new ApiException(ErrorCode.AGENT_LAUNCH_FAILED, "Failed to launch agent.", e);
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
    watcherLauncher.launch(cmd, watchLog);
  }

  static void launchWatcherProcess(List<String> command, Path logPath) throws IOException {
    new ProcessBuilder(command)
        .redirectOutput(ProcessBuilder.Redirect.to(logPath.toFile()))
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
      throw new ApiException(ErrorCode.AGENT_STATUS_FAILED, "Failed to query agent status.", e);
    }
  }

  private static AgentConfigView agentConfigView(SingYaml config) {
    var agent = config.agent();
    return new AgentConfigView(
        agent.type(), agent.autoSnapshot(), agent.autoBranch(), agent.specsDir());
  }

  private static AgentStatusView agentStatusView(
      SingYaml config, String mode, AgentSession.SessionInfo info) {
    return new AgentStatusView(
        config.agent() != null ? config.agent().type() : "",
        mode,
        info != null && info.running(),
        info != null ? info.pid() : null,
        info != null ? info.task() : null,
        info != null ? info.startedAt() : null,
        info != null ? info.branch() : null,
        info != null ? info.logPath() : null);
  }

  private static AgentReportResponse agentReportView(AgentReporter.Report report) {
    return new AgentReportResponse(
        report.name(),
        report.sessionStatus(),
        report.startedAt(),
        report.endedAt(),
        report.duration(),
        report.branch(),
        report.specs().stream().map(spec -> specView(report.specs(), spec)).toList(),
        report.commitCount(),
        report.lastCommitMinutesAgo() >= 0 ? report.lastCommitMinutesAgo() : null,
        report.guardrailTriggered(),
        report.guardrailReason(),
        report.guardrailAction(),
        report.rolledBack(),
        report.rollbackSnapshot());
  }

  private static SpecSummaryView summaryView(java.util.Map<String, Integer> counts) {
    return new SpecSummaryView(
        counts.getOrDefault("pending", 0),
        counts.getOrDefault("in_progress", 0),
        counts.getOrDefault("review", 0),
        counts.getOrDefault("done", 0));
  }

  private static BoardSummaryView boardSummaryView(SpecDirectory.Summary summary) {
    return new BoardSummaryView(
        summaryView(summary.counts()),
        summary.readyCount(),
        summary.blockedCount(),
        summary.nextReadyId());
  }

  private ShellExec.Result exec(List<String> command) {
    try {
      return shell.exec(command);
    } catch (Exception e) {
      throw new ApiException(ErrorCode.COMMAND_FAILED, "A sing system command failed.", e);
    }
  }

  private static <T> Result<T> safe(Supplier<T> supplier) {
    try {
      return Result.success(supplier.get());
    } catch (ApiException e) {
      return e.failure().asFailure();
    } catch (Exception e) {
      return Result.failure(ErrorCode.INTERNAL, "sing API operation failed.", e);
    }
  }

  private record LoadedProject(SingYaml config, ContainerState state) {}

  @FunctionalInterface
  interface WatcherLauncher {
    void launch(List<String> command, Path logPath) throws IOException;
  }
}
