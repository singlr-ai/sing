/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.api;

import ai.singlr.sail.config.Notifications;

/**
 * Looks up a project's {@link Notifications} configuration on demand. Abstracted so the {@link
 * WebhookReactor} can be tested without filesystem I/O, and so future implementations can cache or
 * subscribe to config changes.
 */
@FunctionalInterface
public interface ProjectNotificationsResolver {

  /** Returns the project's notifications config, or {@code null} when none is declared. */
  Notifications resolve(String project);
}
