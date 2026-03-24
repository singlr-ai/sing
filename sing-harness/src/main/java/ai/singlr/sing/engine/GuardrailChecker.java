/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import ai.singlr.sing.config.Guardrails;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Checks agent guardrails. The only enforced guardrail is wall-clock duration — a hard time limit
 * that Sing can check deterministically. Git activity queries are kept for informational use by
 * {@code agent status} and {@code agent report}, not as guardrail triggers.
 */
public final class GuardrailChecker {

  private final ShellExec shell;

  public GuardrailChecker(ShellExec shell) {
    this.shell = shell;
  }

  /** Result of checking guardrails — either everything is fine, or a guardrail was triggered. */
  public sealed interface GuardrailResult {
    record Ok() implements GuardrailResult {}

    record Triggered(String reason, String detail, String action) implements GuardrailResult {}
  }

  /** Git activity snapshot for a single repo. Used by status and report, not by guardrails. */
  public record GitActivity(int commitCount, long lastCommitEpoch) {}

  /**
   * Checks the wall-clock guardrail. Returns {@link GuardrailResult.Triggered} if the agent has
   * been running longer than the configured maximum duration.
   *
   * @param containerName the Incus container name
   * @param guardrails the guardrail configuration
   * @param startedAt when the agent session started
   * @param repoPaths unused, kept for API compatibility
   */
  public GuardrailResult check(
      String containerName, Guardrails guardrails, Instant startedAt, List<String> repoPaths)
      throws IOException, InterruptedException, TimeoutException {

    var maxDuration = Guardrails.parseDuration(guardrails.maxDuration());
    if (maxDuration != null) {
      var elapsed = Duration.between(startedAt, Instant.now());
      if (elapsed.compareTo(maxDuration) > 0) {
        var elapsedStr = formatDuration(elapsed);
        return new GuardrailResult.Triggered(
            "max_duration",
            "Agent running for " + elapsedStr + " (limit: " + guardrails.maxDuration() + ")",
            guardrails.action());
      }
    }

    return new GuardrailResult.Ok();
  }

  /**
   * Queries git activity for a single repo inside the container. Returns commit count since the
   * given instant and the epoch timestamp of the last commit. Used by {@code agent status} and
   * {@code agent report} for informational display.
   *
   * @param containerName the Incus container name
   * @param repoPath absolute path to the repo inside the container
   * @param since count commits after this instant
   */
  public GitActivity queryGitActivity(String containerName, String repoPath, Instant since)
      throws IOException, InterruptedException, TimeoutException {

    var logCmd =
        ContainerExec.asDevUser(
            containerName, List.of("git", "-C", repoPath, "log", "-1", "--format=%ct"));
    var logResult = shell.exec(logCmd);
    var lastCommitEpoch = 0L;
    if (logResult.ok() && !logResult.stdout().isBlank()) {
      try {
        lastCommitEpoch = Long.parseLong(logResult.stdout().trim());
      } catch (NumberFormatException ignored) {
      }
    }

    var sinceEpoch = String.valueOf(since.getEpochSecond());
    var countCmd =
        ContainerExec.asDevUser(
            containerName,
            List.of("git", "-C", repoPath, "rev-list", "--count", "--after=" + sinceEpoch, "HEAD"));
    var countResult = shell.exec(countCmd);
    var commitCount = 0;
    if (countResult.ok() && !countResult.stdout().isBlank()) {
      try {
        commitCount = Integer.parseInt(countResult.stdout().trim());
      } catch (NumberFormatException ignored) {
      }
    }

    return new GitActivity(commitCount, lastCommitEpoch);
  }

  /** Formats a duration as a human-readable string like "3h 27m" or "94m". */
  public static String formatDuration(Duration duration) {
    var hours = duration.toHours();
    var minutes = duration.toMinutesPart();
    if (hours > 0) {
      return hours + "h " + minutes + "m";
    }
    return minutes + "m";
  }
}
