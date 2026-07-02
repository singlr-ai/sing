/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.config;

import ai.singlr.sail.common.Strings;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Slack notification configuration, parsed from the {@code slack} block inside {@code
 * notifications} in sail.yaml. Only the channel lives in config; the bot token is resolved from the
 * {@code SAIL_SLACK_TOKEN} environment variable or the file named by {@code SAIL_SLACK_TOKEN_FILE}
 * — never from sail.yaml.
 *
 * @param channel the Slack channel to post to (required, e.g. {@code #sail-activity})
 */
public record SlackNotifications(String channel) {

  public static SlackNotifications fromMap(Map<String, Object> map) {
    var channel = (String) map.get("channel");
    if (Strings.isBlank(channel)) {
      throw new IllegalArgumentException(
          "notifications.slack.channel is required (e.g. \"#sail-activity\").");
    }
    return new SlackNotifications(channel);
  }

  public Map<String, Object> toMap() {
    var map = new LinkedHashMap<String, Object>();
    map.put("channel", channel);
    return map;
  }
}
