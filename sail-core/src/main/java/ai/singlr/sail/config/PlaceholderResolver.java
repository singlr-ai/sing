/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.config;

import ai.singlr.sail.common.Strings;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Resolves {@code ${PLACEHOLDER}} tokens in YAML content from a known, fixed set — a developer's
 * git identity and SSH public key. Unknown placeholders are an error (safety against injection).
 * Values come either from an interactive prompt or, on the provisioning path, from a supplied
 * resolver that reads the local box's identity, so the synced definition stays identity-free until
 * it lands on a box. The placeholder names are public so {@link PersonalFields} can mint them and a
 * box-local identity provider can answer them.
 */
public final class PlaceholderResolver {

  public static final String GIT_NAME = "GIT_NAME";
  public static final String GIT_EMAIL = "GIT_EMAIL";
  public static final String SSH_PUBLIC_KEY = "SSH_PUBLIC_KEY";

  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([A-Z_]+)\\}");

  private static final Map<String, String> KNOWN_PLACEHOLDERS =
      Map.of(
          GIT_NAME, "Your name (for git commits)",
          GIT_EMAIL, "Your email (for git commits)",
          SSH_PUBLIC_KEY, "Your SSH public key (for Zed remote access)");

  private PlaceholderResolver() {}

  /** Returns the {@code ${NAME}} token for a placeholder, e.g. {@code ${GIT_NAME}}. */
  public static String token(String name) {
    return "${" + name + "}";
  }

  /** The human prompt for a known placeholder, so callers needn't restate it. */
  public static String promptFor(String name) {
    var prompt = KNOWN_PLACEHOLDERS.get(name);
    if (prompt == null) {
      throw new IllegalArgumentException(
          "Unknown placeholder: ${"
              + name
              + "}. Known placeholders: "
              + KNOWN_PLACEHOLDERS.keySet());
    }
    return prompt;
  }

  /**
   * Resolves placeholders non-interactively: each unique {@code ${NAME}} is replaced with {@code
   * values.apply(NAME)}. The resolver is consulted only for placeholders actually present, so
   * content with none needs no identity at all. Unknown placeholders and blank values are errors.
   */
  public static String resolve(String content, UnaryOperator<String> values) {
    var matcher = PLACEHOLDER_PATTERN.matcher(content);
    var resolved = new LinkedHashMap<String, String>();
    while (matcher.find()) {
      var name = matcher.group(1);
      if (resolved.containsKey(name)) {
        continue;
      }
      if (!KNOWN_PLACEHOLDERS.containsKey(name)) {
        throw new IllegalArgumentException(
            "Unknown placeholder: ${"
                + name
                + "}. Known placeholders: "
                + KNOWN_PLACEHOLDERS.keySet());
      }
      var value = values.apply(name);
      if (Strings.isBlank(value)) {
        throw new IllegalArgumentException(
            "No value for ${" + name + "} (" + KNOWN_PLACEHOLDERS.get(name) + ")");
      }
      resolved.put(name, value.strip());
    }
    return substitute(content, resolved);
  }

  /**
   * Scans the YAML content for {@code ${...}} placeholders, prompts the user for each unique one,
   * and returns the content with all placeholders replaced.
   *
   * @param content the raw YAML string with placeholders
   * @return the resolved YAML string
   * @throws IllegalArgumentException if an unknown placeholder is found
   */
  public static String resolve(String content) {
    return resolveInteractively(content, null);
  }

  /**
   * Scans the YAML content for placeholders and resolves them by prompting. If a {@code reader} is
   * provided, it is used for input instead of stdin (for testing).
   */
  static String resolveInteractively(String content, BufferedReader reader) {
    var matcher = PLACEHOLDER_PATTERN.matcher(content);
    var found = new LinkedHashMap<String, String>();

    while (matcher.find()) {
      var name = matcher.group(1);
      if (!found.containsKey(name)) {
        if (!KNOWN_PLACEHOLDERS.containsKey(name)) {
          throw new IllegalArgumentException(
              "Unknown placeholder: ${"
                  + name
                  + "}. Known placeholders: "
                  + KNOWN_PLACEHOLDERS.keySet());
        }
        found.put(name, null);
      }
    }

    if (found.isEmpty()) {
      return content;
    }

    var resolved = new LinkedHashMap<String, String>();
    for (var name : found.keySet()) {
      var prompt = KNOWN_PLACEHOLDERS.get(name);
      System.out.print("  " + prompt + ": ");
      System.out.flush();
      var value = readLine(reader);
      if (Strings.isBlank(value)) {
        throw new IllegalArgumentException("Value required for ${" + name + "} (" + prompt + ")");
      }
      resolved.put(name, value.strip());
    }

    return substitute(content, resolved);
  }

  private static String substitute(String content, Map<String, String> resolved) {
    var result = content;
    for (var entry : resolved.entrySet()) {
      result = result.replace(token(entry.getKey()), entry.getValue());
    }
    return result;
  }

  private static String readLine(BufferedReader reader) {
    try {
      if (reader != null) {
        return reader.readLine();
      }
      var console = System.console();
      if (console != null) {
        return console.readLine();
      }
      return new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))
          .readLine();
    } catch (Exception e) {
      return null;
    }
  }
}
