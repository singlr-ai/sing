/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

/**
 * Models the possible states of an Incus container, queried live from {@code incus list --format
 * json}. Used by {@link ContainerManager} and all lifecycle commands.
 */
public sealed interface ContainerState {

  /** Container does not exist — {@code incus list} returned an empty array. */
  record NotCreated() implements ContainerState {}

  /** Container is running. {@code ipv4} may be null if no address is assigned yet. */
  record Running(String ipv4) implements ContainerState {}

  /** Container exists but is stopped. */
  record Stopped() implements ContainerState {}

  /** Container is in an unexpected state (e.g. Frozen, Error). */
  record Error(String message) implements ContainerState {}
}
