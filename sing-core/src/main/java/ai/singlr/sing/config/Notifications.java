/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.config;

import java.net.InetAddress;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Webhook notification configuration for agent watch events. Parsed from the {@code notifications}
 * block inside {@code agent} in sail.yaml.
 *
 * @param url the webhook endpoint URL (required, must be https:// or http:// with SSRF checks)
 * @param events which events to notify on (null or empty means all events)
 */
public record Notifications(String url, List<String> events) {

  /** Known event types that can trigger notifications. */
  public static final Set<String> VALID_EVENTS =
      Set.of("guardrail_triggered", "agent_exited", "session_done");

  @SuppressWarnings("unchecked")
  public static Notifications fromMap(Map<String, Object> map) {
    var url = (String) map.get("url");
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("notifications.url is required.");
    }
    requireSafeWebhookUrl(url);

    var eventsRaw = (List<String>) map.get("events");
    if (eventsRaw != null) {
      for (var event : eventsRaw) {
        if (!VALID_EVENTS.contains(event)) {
          throw new IllegalArgumentException(
              "Unknown notification event: '"
                  + event
                  + "'. Valid events: "
                  + String.join(", ", VALID_EVENTS));
        }
      }
    }

    return new Notifications(url, eventsRaw);
  }

  public Map<String, Object> toMap() {
    var map = new LinkedHashMap<String, Object>();
    map.put("url", url);
    if (events != null && !events.isEmpty()) {
      map.put("events", List.copyOf(events));
    }
    return map;
  }

  /** Returns true if the given event should trigger a notification. */
  public boolean shouldNotify(String event) {
    return events == null || events.isEmpty() || events.contains(event);
  }

  /**
   * Validates a webhook URL: must be http(s) and must not target private/internal networks. Package
   * private for testing.
   */
  static void requireSafeWebhookUrl(String url) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Invalid notifications.url: '" + url + "'. Must be a valid URL.");
    }

    var scheme = uri.getScheme();
    if (scheme == null || (!scheme.equals("https") && !scheme.equals("http"))) {
      throw new IllegalArgumentException(
          "Invalid notifications.url scheme: '"
              + scheme
              + "'. Only https:// and http:// are allowed.");
    }

    var host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException(
          "Invalid notifications.url: '" + url + "'. No hostname found.");
    }

    if (isPrivateHost(host)) {
      throw new IllegalArgumentException(
          "Invalid notifications.url: '"
              + url
              + "'. Private/internal network addresses are not allowed.");
    }
  }

  /** Checks if a hostname resolves to a private or loopback address. */
  private static boolean isPrivateHost(String host) {
    var lower = host.toLowerCase();
    if (lower.equals("localhost") || lower.equals("[::1]")) {
      return true;
    }

    var bare =
        lower.startsWith("[") && lower.endsWith("]")
            ? lower.substring(1, lower.length() - 1)
            : lower;

    if (bare.startsWith("127.")
        || bare.startsWith("10.")
        || bare.startsWith("192.168.")
        || bare.equals("169.254.169.254")
        || bare.equals("0.0.0.0")
        || bare.equals("::1")) {
      return true;
    }

    if (bare.startsWith("172.")) {
      try {
        var parts = bare.split("\\.");
        if (parts.length >= 2) {
          var secondOctet = Integer.parseInt(parts[1]);
          if (secondOctet >= 16 && secondOctet <= 31) {
            return true;
          }
        }
      } catch (NumberFormatException ignored) {
      }
    }

    if (bare.startsWith("fd") || bare.startsWith("fc") || bare.startsWith("fe80")) {
      return true;
    }

    try {
      var addr = InetAddress.getByName(bare);
      return addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress();
    } catch (Exception e) {
      return true;
    }
  }
}
