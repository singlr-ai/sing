/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.config;

import java.net.InetAddress;
import java.net.URI;

/**
 * SSRF policy for outbound webhook URLs. Validation runs at config parse time (via {@link
 * Notifications}); the host check is also re-run at send time as defense-in-depth against
 * DNS-rebinding, since a name that resolved to a public address when the config was parsed may
 * resolve to a private one by the time the request is made.
 */
public final class WebhookUrlSafety {

  private WebhookUrlSafety() {}

  /**
   * Validates that {@code url} is an http(s) URL whose host does not target a private/internal
   * network. Throws {@link IllegalArgumentException} on any violation.
   */
  public static void requireSafe(String url) {
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

  /** Returns true if the hostname is, or resolves to, a private/loopback/link-local address. */
  public static boolean isPrivateHost(String host) {
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
