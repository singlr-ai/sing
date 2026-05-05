/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent methodology configuration. Defines how the agent should approach work — spec-driven
 * development, TDD, or free-form — and what verification commands to run.
 *
 * <p>YAML key: {@code agent.methodology}
 *
 * <pre>{@code
 * agent:
 *   methodology:
 *     approach: spec-driven   # spec-driven | tdd | free-form
 *     verify: "mvn test"      # verification command (run after implementation)
 *     lint: "mvn spotless:check"  # lint command (run after each edit)
 * }</pre>
 */
public record Methodology(String approach, String verify, String lint) {

  @SuppressWarnings("unchecked")
  public static Methodology fromMap(Map<String, Object> map) {
    return new Methodology(
        (String) map.get("approach"), (String) map.get("verify"), (String) map.get("lint"));
  }

  public Map<String, Object> toMap() {
    var map = new LinkedHashMap<String, Object>();
    if (approach != null) map.put("approach", approach);
    if (verify != null) map.put("verify", verify);
    if (lint != null) map.put("lint", lint);
    return map;
  }
}
