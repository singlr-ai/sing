/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SecurityAuditTest {

  @Test
  void fromMapParsesEnabledWithAuditor() {
    var audit = SecurityAudit.fromMap(Map.of("enabled", true, "auditor", "codex"));

    assertTrue(audit.enabled());
    assertEquals("codex", audit.auditor());
  }

  @Test
  void fromMapDisabledByDefault() {
    var audit = SecurityAudit.fromMap(Map.of("auditor", "codex"));

    assertFalse(audit.enabled());
    assertEquals("codex", audit.auditor());
  }

  @Test
  void fromMapExplicitlyDisabled() {
    var audit = SecurityAudit.fromMap(Map.of("enabled", false, "auditor", "codex"));

    assertFalse(audit.enabled());
  }

  @Test
  void fromMapEnabledWithoutAuditorUsesAutoResolution() {
    var audit = SecurityAudit.fromMap(Map.of("enabled", true));

    assertTrue(audit.enabled());
    assertNull(audit.auditor());
  }

  @Test
  void fromMapAllowsDisabledWithoutAuditor() {
    var audit = SecurityAudit.fromMap(Map.of("enabled", false));

    assertFalse(audit.enabled());
    assertNull(audit.auditor());
  }

  @Test
  void resolveAuditorUsesExplicitOverride() {
    var audit = new SecurityAudit(true, "gemini");

    assertEquals("gemini", audit.resolveAuditor("claude-code", List.of("codex", "gemini")));
  }

  @Test
  void resolveAuditorPicksFirstNonPrimary() {
    var audit = new SecurityAudit(true, null);

    assertEquals("codex", audit.resolveAuditor("claude-code", List.of("claude-code", "codex")));
  }

  @Test
  void resolveAuditorSkipsPrimaryWhenFirst() {
    var audit = new SecurityAudit(true, null);

    assertEquals(
        "codex", audit.resolveAuditor("claude-code", List.of("claude-code", "codex", "gemini")));
  }

  @Test
  void resolveAuditorPicksFirstNonPrimaryFromMultiple() {
    var audit = new SecurityAudit(true, null);

    assertEquals("gemini", audit.resolveAuditor("claude-code", List.of("gemini", "codex")));
  }

  @Test
  void resolveAuditorFallsBackToPrimaryWhenOnlyPrimaryInstalled() {
    var audit = new SecurityAudit(true, null);

    assertEquals("claude-code", audit.resolveAuditor("claude-code", List.of("claude-code")));
  }

  @Test
  void resolveAuditorReturnsNullWhenInstallListNull() {
    var audit = new SecurityAudit(true, null);

    assertNull(audit.resolveAuditor("claude-code", null));
  }

  @Test
  void resolveAuditorReturnsNullWhenInstallListEmpty() {
    var audit = new SecurityAudit(true, null);

    assertNull(audit.resolveAuditor("claude-code", List.of()));
  }

  @Test
  void resolveAuditorWithTwoNonPrimaryPicksFirst() {
    var audit = new SecurityAudit(true, null);

    assertEquals("codex", audit.resolveAuditor("gemini", List.of("codex", "claude-code")));
  }

  @Test
  void resolveAuditorRejectsUnknownAgent() {
    var audit = new SecurityAudit(true, "unknown-agent");

    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> audit.resolveAuditor("claude-code", List.of("codex")));

    assertTrue(ex.getMessage().contains("Unknown security auditor 'unknown-agent'"));
    assertTrue(ex.getMessage().contains("Known agents:"));
  }

  @Test
  void resolveAuditorAllowsSameAsPrimary() {
    var audit = new SecurityAudit(true, "claude-code");

    assertEquals(
        "claude-code", audit.resolveAuditor("claude-code", List.of("claude-code", "codex")));
  }

  @Test
  void resolveAuditorRejectsNotInInstallList() {
    var audit = new SecurityAudit(true, "gemini");

    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> audit.resolveAuditor("claude-code", List.of("codex")));

    assertTrue(ex.getMessage().contains("is not in the agent install list"));
    assertTrue(ex.getMessage().contains("Add 'gemini' to 'agent.install'"));
  }

  @Test
  void resolveAuditorRejectsNotInInstallListWhenNull() {
    var audit = new SecurityAudit(true, "codex");

    var ex =
        assertThrows(
            IllegalArgumentException.class, () -> audit.resolveAuditor("claude-code", null));

    assertTrue(ex.getMessage().contains("is not in the agent install list"));
  }
}
