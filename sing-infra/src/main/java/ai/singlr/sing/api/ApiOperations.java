/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.api;

public interface ApiOperations {
  Result<HealthResponse> health();

  Result<ProjectResponse> project(String project);

  Result<SpecsResponse> specs(String project);

  Result<SpecResponse> spec(String project, String specId);

  Result<DispatchResponse> dispatch(String project, DispatchRequest request);

  Result<AgentStatusResponse> agentStatus(String project);

  Result<AgentLogResponse> agentLog(String project, int tail);

  Result<StopAgentResponse> stopAgent(String project);

  Result<AgentReportResponse> agentReport(String project);
}
