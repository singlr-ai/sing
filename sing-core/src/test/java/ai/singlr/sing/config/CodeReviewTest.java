/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CodeReviewTest {

  @Test
  void fromMapParsesEnabledWithAuditor() {
    var review = CodeReview.fromMap(Map.of("enabled", true, "auditor", "gemini"));

    assertTrue(review.enabled());
    assertEquals("gemini", review.auditor());
  }

  @Test
  void fromMapDisabledByDefault() {
    var review = CodeReview.fromMap(Map.of("auditor", "gemini"));

    assertFalse(review.enabled());
    assertEquals("gemini", review.auditor());
  }

  @Test
  void fromMapExplicitlyDisabled() {
    var review = CodeReview.fromMap(Map.of("enabled", false, "auditor", "gemini"));

    assertFalse(review.enabled());
  }

  @Test
  void fromMapEnabledWithoutAuditorUsesAutoResolution() {
    var review = CodeReview.fromMap(Map.of("enabled", true));

    assertTrue(review.enabled());
    assertNull(review.auditor());
  }

  @Test
  void fromMapAllowsDisabledWithoutAuditor() {
    var review = CodeReview.fromMap(Map.of("enabled", false));

    assertFalse(review.enabled());
    assertNull(review.auditor());
  }

  @Test
  void resolveAuditorUsesExplicitOverride() {
    var review = new CodeReview(true, "gemini");

    assertEquals(
        "gemini", review.resolveAuditor("claude-code", List.of("codex", "gemini"), Set.of()));
  }

  @Test
  void resolveAuditorPicksFirstNonPrimaryNonExcluded() {
    var review = new CodeReview(true, null);

    assertEquals(
        "gemini",
        review.resolveAuditor(
            "claude-code", List.of("claude-code", "codex", "gemini"), Set.of("codex")));
  }

  @Test
  void resolveAuditorSkipsPrimaryAndExcluded() {
    var review = new CodeReview(true, null);

    assertEquals(
        "gemini",
        review.resolveAuditor(
            "claude-code", List.of("claude-code", "codex", "gemini"), Set.of("codex")));
  }

  @Test
  void resolveAuditorPicksFirstNonPrimaryWhenNoExclusions() {
    var review = new CodeReview(true, null);

    assertEquals(
        "codex",
        review.resolveAuditor("claude-code", List.of("claude-code", "codex", "gemini"), Set.of()));
  }

  @Test
  void resolveAuditorFallsBackToPrimaryWhenOnlyNonPrimaryExcluded() {
    var review = new CodeReview(true, null);

    assertEquals(
        "claude-code",
        review.resolveAuditor("claude-code", List.of("claude-code", "codex"), Set.of("codex")));
  }

  @Test
  void resolveAuditorFallsBackToPrimaryWhenOnlyPrimaryInstalled() {
    var review = new CodeReview(true, null);

    assertEquals(
        "claude-code", review.resolveAuditor("claude-code", List.of("claude-code"), Set.of()));
  }

  @Test
  void resolveAuditorReturnsNullWhenInstallListNull() {
    var review = new CodeReview(true, null);

    assertNull(review.resolveAuditor("claude-code", null, Set.of()));
  }

  @Test
  void resolveAuditorReturnsNullWhenInstallListEmpty() {
    var review = new CodeReview(true, null);

    assertNull(review.resolveAuditor("claude-code", List.of(), Set.of()));
  }

  @Test
  void resolveAuditorHandlesNullExcludeSet() {
    var review = new CodeReview(true, null);

    assertEquals(
        "codex", review.resolveAuditor("claude-code", List.of("claude-code", "codex"), null));
  }

  @Test
  void resolveAuditorRejectsUnknownAgent() {
    var review = new CodeReview(true, "unknown-agent");

    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> review.resolveAuditor("claude-code", List.of("codex"), Set.of()));

    assertTrue(ex.getMessage().contains("Unknown code review auditor 'unknown-agent'"));
    assertTrue(ex.getMessage().contains("Known agents:"));
  }

  @Test
  void resolveAuditorAllowsSameAsPrimary() {
    var review = new CodeReview(true, "claude-code");

    assertEquals(
        "claude-code",
        review.resolveAuditor("claude-code", List.of("claude-code", "codex"), Set.of()));
  }

  @Test
  void resolveAuditorRejectsNotInInstallList() {
    var review = new CodeReview(true, "gemini");

    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> review.resolveAuditor("claude-code", List.of("codex"), Set.of()));

    assertTrue(ex.getMessage().contains("is not in the agent install list"));
    assertTrue(ex.getMessage().contains("Add 'gemini' to 'agent.install'"));
  }

  @Test
  void resolveAuditorRejectsNotInInstallListWhenNull() {
    var review = new CodeReview(true, "codex");

    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> review.resolveAuditor("claude-code", null, Set.of()));

    assertTrue(ex.getMessage().contains("is not in the agent install list"));
  }

  @Test
  void toMapSerializesEnabled() {
    var review = new CodeReview(true, null);
    var map = review.toMap();

    assertEquals(true, map.get("enabled"));
    assertFalse(map.containsKey("auditor"));
  }

  @Test
  void toMapSerializesAuditor() {
    var review = new CodeReview(true, "gemini");
    var map = review.toMap();

    assertEquals(true, map.get("enabled"));
    assertEquals("gemini", map.get("auditor"));
  }
}
