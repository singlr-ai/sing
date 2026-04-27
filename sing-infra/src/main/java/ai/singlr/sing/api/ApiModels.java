/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.api;

import java.util.List;

record HealthResponse(String status) {}

record ProjectResponse(String name, String containerStatus, AgentConfigView agent) {}

record AgentConfigView(String type, boolean autoSnapshot, boolean autoBranch, String specsDir) {}

record SpecsResponse(
    String name, List<SpecView> specs, SpecSummaryView counts, BoardSummaryView summary) {}

record SpecResponse(
    String name, SpecView spec, String specPath, boolean contentAvailable, String content) {}

record DispatchRequest(String specId, String mode, boolean dryRun) {
  DispatchRequest {
    mode = mode == null || mode.isBlank() ? "background" : mode;
  }
}

record DispatchResponse(
    String name,
    boolean dispatched,
    String reason,
    DispatchedSpecView spec,
    AgentStatusView agent,
    String snapshot,
    boolean branchCreated) {}

record AgentStatusResponse(
    String name,
    boolean agentRunning,
    Integer pid,
    String task,
    String startedAt,
    String branch,
    String logPath) {}

record AgentLogResponse(String name, List<String> lines, String error) {}

record StopAgentResponse(String name, boolean stopped, String reason, Integer pid) {}

record AgentReportResponse(
    String name,
    String sessionStatus,
    String startedAt,
    String endedAt,
    String duration,
    String branch,
    List<SpecView> specs,
    int commitsSinceLaunch,
    Long lastCommitMinutesAgo,
    boolean guardrailTriggered,
    String guardrailReason,
    String guardrailAction,
    boolean rolledBack,
    String rollbackSnapshot) {}

record SpecView(
    String id,
    String title,
    String status,
    String assignee,
    List<String> dependsOn,
    String branch,
    boolean ready,
    boolean blocked,
    List<String> unmetDependencies) {
  public SpecView {
    dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    unmetDependencies = unmetDependencies == null ? List.of() : List.copyOf(unmetDependencies);
  }
}

record DispatchedSpecView(String id, String title, String status, String branch) {}

record AgentStatusView(
    String type,
    String mode,
    boolean running,
    Integer pid,
    String task,
    String startedAt,
    String branch,
    String logPath) {}

record SpecSummaryView(int pending, int inProgress, int review, int done) {}

record BoardSummaryView(
    SpecSummaryView counts, int readyCount, int blockedCount, String nextReadyId) {}

record ErrorResponse(ApiError error) {}
