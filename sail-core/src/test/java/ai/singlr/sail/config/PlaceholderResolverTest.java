/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlaceholderResolverTest {

  @Test
  void resolvesNonInteractivelyFromAProvider() {
    var content = "git:\n  name: ${GIT_NAME}\n  email: ${GIT_EMAIL}\n";
    var values = Map.of("GIT_NAME", "Mady M", "GIT_EMAIL", "mady@example.com");

    var result = PlaceholderResolver.resolve(content, values::get);

    assertEquals("git:\n  name: Mady M\n  email: mady@example.com\n", result);
  }

  @Test
  void providerIsConsultedOnlyForPresentPlaceholders() {
    var content = "name: acme\n";
    var result =
        PlaceholderResolver.resolve(
            content,
            name -> {
              throw new AssertionError("provider must not be called: " + name);
            });
    assertEquals(content, result);
  }

  @Test
  void providerResolveRejectsUnknownPlaceholders() {
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> PlaceholderResolver.resolve("x: ${NOPE}\n", name -> "v"));
    assertTrue(ex.getMessage().contains("Unknown placeholder"));
  }

  @Test
  void providerResolveRejectsBlankValues() {
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> PlaceholderResolver.resolve("x: ${GIT_NAME}\n", name -> "  "));
    assertTrue(ex.getMessage().contains("GIT_NAME"));
  }

  @Test
  void tokenWrapsAName() {
    assertEquals("${GIT_NAME}", PlaceholderResolver.token(PlaceholderResolver.GIT_NAME));
  }

  @Test
  void resolvesKnownPlaceholders() {
    var content =
        """
        git:
          name: "${GIT_NAME}"
          email: "${GIT_EMAIL}"
        """;
    var reader = new BufferedReader(new StringReader("Alice\nalice@example.com\n"));

    var result = PlaceholderResolver.resolveInteractively(content, reader);

    assertTrue(result.contains("Alice"));
    assertTrue(result.contains("alice@example.com"));
    assertFalse(result.contains("${GIT_NAME}"));
    assertFalse(result.contains("${GIT_EMAIL}"));
  }

  @Test
  void multipleSamePlaceholderPromptedOnce() {
    var content = "name: ${GIT_NAME}\nauthor: ${GIT_NAME}\n";
    var reader = new BufferedReader(new StringReader("Bob\n"));

    var result = PlaceholderResolver.resolveInteractively(content, reader);

    assertEquals("name: Bob\nauthor: Bob\n", result);
  }

  @Test
  void unknownPlaceholderThrows() {
    var content = "value: ${UNKNOWN_VAR}\n";
    var reader = new BufferedReader(new StringReader("anything\n"));

    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> PlaceholderResolver.resolveInteractively(content, reader));
    assertTrue(ex.getMessage().contains("Unknown placeholder"));
    assertTrue(ex.getMessage().contains("UNKNOWN_VAR"));
  }

  @Test
  void noPlaceholdersReturnsUnchanged() {
    var content = "name: acme-health\nimage: ubuntu/24.04\n";

    var result = PlaceholderResolver.resolveInteractively(content, null);

    assertEquals(content, result);
  }

  @Test
  void sshPublicKeyPlaceholder() {
    var content =
        """
        ssh:
          user: dev
          authorized_keys:
            - "${SSH_PUBLIC_KEY}"
        """;
    var reader = new BufferedReader(new StringReader("ssh-ed25519 AAAA... alice@laptop\n"));

    var result = PlaceholderResolver.resolveInteractively(content, reader);

    assertTrue(result.contains("ssh-ed25519 AAAA... alice@laptop"));
    assertFalse(result.contains("${SSH_PUBLIC_KEY}"));
  }

  @Test
  void blankInputThrows() {
    var content = "name: ${GIT_NAME}\n";
    var reader = new BufferedReader(new StringReader("  \n"));

    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> PlaceholderResolver.resolveInteractively(content, reader));
    assertTrue(ex.getMessage().contains("Value required"));
    assertTrue(ex.getMessage().contains("GIT_NAME"));
  }

  @Test
  void allThreePlaceholders() {
    var content =
        """
        git:
          name: "${GIT_NAME}"
          email: "${GIT_EMAIL}"
        ssh:
          authorized_keys:
            - "${SSH_PUBLIC_KEY}"
        """;
    var reader = new BufferedReader(new StringReader("Carol\ncarol@co.com\nssh-ed25519 AAAA...\n"));

    var result = PlaceholderResolver.resolveInteractively(content, reader);

    assertTrue(result.contains("Carol"));
    assertTrue(result.contains("carol@co.com"));
    assertTrue(result.contains("ssh-ed25519 AAAA..."));
  }
}
