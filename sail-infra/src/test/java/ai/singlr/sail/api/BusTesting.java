/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.api;

import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;

/**
 * Test support for the event bus. Delivery happens on a per-subscriber drain thread, so a test must
 * wait for processing rather than sleep. {@link #latching} wraps the real subscriber and counts a
 * latch down after each {@code onEvent} — even on failure — so a test can block until the
 * subscriber-under-test has actually handled the event, deterministically and without a fixed
 * sleep.
 */
final class BusTesting {

  private BusTesting() {}

  static EventSubscriber latching(EventSubscriber delegate, CountDownLatch latch) {
    return new EventSubscriber() {
      @Override
      public String name() {
        return delegate.name();
      }

      @Override
      public Predicate<Event> filter() {
        return delegate.filter();
      }

      @Override
      public void onEvent(Event event) throws Exception {
        try {
          delegate.onEvent(event);
        } finally {
          latch.countDown();
        }
      }
    };
  }
}
