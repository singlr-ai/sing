/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.gen;

import static org.junit.jupiter.api.Assertions.*;

import ai.singlr.sing.config.CodeReview;
import ai.singlr.sing.config.SingYaml;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CodeReviewGeneratorTest {

  @Test
  void reviewScriptContainsReviewerBinary() {
    var script = CodeReviewGenerator.generateReviewScript("gemini");

    assertTrue(script.contains("gemini --print"));
  }

  @Test
  void reviewScriptContainsBugChecklist() {
    var script = CodeReviewGenerator.generateReviewScript("gemini");

    assertTrue(script.contains("Logic errors"));
    assertTrue(script.contains("Edge cases"));
    assertTrue(script.contains("Error handling"));
    assertTrue(script.contains("Resource leaks"));
    assertTrue(script.contains("Concurrency issues"));
    assertTrue(script.contains("Performance pitfalls"));
    assertTrue(script.contains("API contract violations"));
    assertTrue(script.contains("Data integrity"));
  }

  @Test
  void reviewScriptUsesGitDiff() {
    var script = CodeReviewGenerator.generateReviewScript("gemini");

    assertTrue(script.contains("git diff main..HEAD"));
  }

  @Test
  void reviewScriptExcludesLockFiles() {
    var script = CodeReviewGenerator.generateReviewScript("gemini");

    assertTrue(script.contains("':!*.lock'"));
    assertTrue(script.contains("':!*.min.js'"));
    assertTrue(script.contains("':!*.min.css'"));
  }

  @Test
  void reviewScriptWritesResultFile() {
    var script = CodeReviewGenerator.generateReviewScript("gemini");

    assertTrue(script.contains("~/code-review.md"));
  }

  @Test
  void reviewScriptExitsWithCode2OnFailure() {
    var script = CodeReviewGenerator.generateReviewScript("gemini");

    assertTrue(script.contains("exit 2"));
  }

  @Test
  void reviewScriptUsesCodexBinaryWhenConfigured() {
    var script = CodeReviewGenerator.generateReviewScript("codex");

    assertTrue(script.contains("codex --print"));
    assertFalse(script.contains("gemini"));
  }

  @Test
  void generateFilesReturnsEmptyWhenNoCodeReview() {
    var agent =
        new SingYaml.Agent(
            "claude-code", true, "sing/", true, null, null, null, null, null, null, null);
    var config = minimalConfig(agent);

    var files = CodeReviewGenerator.generateFiles(config, Set.of());

    assertTrue(files.isEmpty());
  }

  @Test
  void generateFilesReturnsEmptyWhenDisabled() {
    var review = new CodeReview(false, "gemini");
    var agent =
        new SingYaml.Agent(
            "claude-code", true, "sing/", true, null, null, null, null, null, review, null);
    var config = minimalConfig(agent);

    var files = CodeReviewGenerator.generateFiles(config, Set.of());

    assertTrue(files.isEmpty());
  }

  @Test
  void generateFilesReturnsScriptOnly() {
    var review = new CodeReview(true, "gemini");
    var agent =
        new SingYaml.Agent(
            "claude-code",
            true,
            "sing/",
            true,
            List.of("gemini"),
            null,
            null,
            null,
            null,
            review,
            null);
    var config = minimalConfig(agent);

    var files = CodeReviewGenerator.generateFiles(config, Set.of());

    assertEquals(1, files.size());
    var script = files.getFirst();
    assertTrue(script.remotePath().endsWith("code-review.sh"));
    assertTrue(script.executable());
    assertTrue(script.content().contains("gemini --print"));
  }

  @Test
  void generateFilesAutoResolvesReviewerSkippingExcluded() {
    var review = new CodeReview(true, null);
    var agent =
        new SingYaml.Agent(
            "claude-code",
            true,
            "sing/",
            true,
            List.of("claude-code", "codex", "gemini"),
            null,
            null,
            null,
            null,
            review,
            null);
    var config = minimalConfig(agent);

    var files = CodeReviewGenerator.generateFiles(config, Set.of("codex"));

    assertEquals(1, files.size());
    assertTrue(files.getFirst().content().contains("gemini --print"));
  }

  @Test
  void generateFilesSelfAuditsWhenAllNonPrimaryExcluded() {
    var review = new CodeReview(true, null);
    var agent =
        new SingYaml.Agent(
            "claude-code",
            true,
            "sing/",
            true,
            List.of("claude-code", "codex"),
            null,
            null,
            null,
            null,
            review,
            null);
    var config = minimalConfig(agent);

    var files = CodeReviewGenerator.generateFiles(config, Set.of("codex"));

    assertEquals(1, files.size());
    assertTrue(files.getFirst().content().contains("claude --print"));
  }

  @Test
  void generateFilesUsesCorrectSshUser() {
    var review = new CodeReview(true, "codex");
    var agent =
        new SingYaml.Agent(
            "claude-code",
            true,
            "sing/",
            true,
            List.of("codex"),
            null,
            null,
            null,
            null,
            review,
            null);
    var ssh = new SingYaml.Ssh("alice", null);
    var config =
        new SingYaml(
            "test",
            null,
            new SingYaml.Resources(2, "4GB", "50GB"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            agent,
            null,
            ssh);

    var files = CodeReviewGenerator.generateFiles(config, Set.of());

    assertTrue(files.getFirst().remotePath().contains("/home/alice/"));
  }

  @Test
  void generateFilesThrowsWhenAuditorIsUnknown() {
    var review = new CodeReview(true, "unknown-agent");
    var agent =
        new SingYaml.Agent(
            "claude-code",
            true,
            "sing/",
            true,
            List.of("codex"),
            null,
            null,
            null,
            null,
            review,
            null);
    var config = minimalConfig(agent);

    assertThrows(
        IllegalArgumentException.class, () -> CodeReviewGenerator.generateFiles(config, Set.of()));
  }

  private static SingYaml minimalConfig(SingYaml.Agent agent) {
    return new SingYaml(
        "test",
        null,
        new SingYaml.Resources(2, "4GB", "50GB"),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        agent,
        null,
        null);
  }
}
