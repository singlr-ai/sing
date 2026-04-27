/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.api;

import java.util.Map;

public interface ApiOperations {
  Map<String, Object> health();

  Map<String, Object> project(String project);

  Map<String, Object> specs(String project);

  Map<String, Object> spec(String project, String specId);

  Map<String, Object> dispatch(String project, Map<String, Object> request);

  Map<String, Object> agentStatus(String project);

  Map<String, Object> agentLog(String project, int tail);

  Map<String, Object> stopAgent(String project);

  Map<String, Object> agentReport(String project);
}
