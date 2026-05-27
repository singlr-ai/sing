/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.engine;

import java.util.List;

/**
 * Builds structured review prompts for review agents. The prompt instructs the agent to output
 * findings as a JSON array with severity, category, file/line references, evidence, and suggested
 * fixes. Every finding must include evidence — unsubstantiated reports are explicitly excluded.
 */
public final class ReviewPromptBuilder {

  private ReviewPromptBuilder() {}

  public static String build(String branch, String repo, List<String> categories) {
    var categoryList =
        categories.isEmpty() ? "any relevant category" : String.join(", ", categories);

    return """
        Review the changes on branch %s in repository %s.

        Focus on these categories: %s

        Output your findings as a JSON array. Each finding must have:
        - severity: CRITICAL, HIGH, MEDIUM, or LOW
        - category: one of SECURITY, LOGIC, EDGE_CASE, PERFORMANCE, ERROR_HANDLING, CONCURRENCY, RESOURCE_LEAK, API_CONTRACT
        - file: relative file path
        - line_start: first affected line number
        - line_end: last affected line number (same as line_start for single-line issues)
        - title: one-line summary of the issue
        - description: detailed explanation of the problem
        - evidence: proof this is a real issue (data flow trace, failing test case, CVE reference, or logical argument)
        - suggestion: an object with "before" (current code), "after" (fixed code), and "rationale" (why the fix works)
        - confidence: 0.0 to 1.0 indicating your certainty

        Rules:
        1. Only report genuine issues. Do not flag style preferences or working code.
        2. Every finding MUST include evidence. If you cannot prove it, do not report it.
        3. Every finding MUST include a concrete suggestion with before/after code.
        4. Focus on correctness, security, and reliability — not formatting.
        5. If there are no issues, return an empty array: []

        Begin your response with ```json and end with ```.
        """
        .formatted(branch, repo, categoryList);
  }
}
