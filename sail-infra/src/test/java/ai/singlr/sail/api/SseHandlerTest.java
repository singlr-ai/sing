/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.api;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import org.junit.jupiter.api.Test;

class SseHandlerTest {

  private static final BearerAuth AUTH = new BearerAuth("test-token");

  @Test
  void constructorRejectsNullBus() {
    assertThrows(NullPointerException.class, () -> new SseHandler(null, AUTH));
  }

  @Test
  void constructorRejectsNullAuth() {
    try (var bus = new EventBus()) {
      assertThrows(NullPointerException.class, () -> new SseHandler(bus, null));
    }
  }

  @Test
  void constructorRejectsNonPositiveMax() {
    try (var bus = new EventBus()) {
      assertThrows(IllegalArgumentException.class, () -> new SseHandler(bus, AUTH, 0));
      assertThrows(IllegalArgumentException.class, () -> new SseHandler(bus, AUTH, -3));
    }
  }

  @Test
  void initialCountersAreZero() {
    try (var bus = new EventBus()) {
      var handler = new SseHandler(bus, AUTH, 4);
      assertEquals(0L, handler.rejectedCount());
      assertEquals(0, handler.openConnections());
    }
  }

  @Test
  void parseFilterReturnsAllForEmptyQuery() {
    var filter = SseHandler.parseFilter(URI.create("http://x/v1/events/stream"));
    assertTrue(filter.test(Event.of("p", null, "t", "a", "h")));
  }

  @Test
  void parseFilterScopesByProject() {
    var filter = SseHandler.parseFilter(URI.create("http://x/v1/events/stream?project=light"));
    assertTrue(filter.test(Event.of("light", null, "t", "a", "h")));
    assertFalse(filter.test(Event.of("dark", null, "t", "a", "h")));
  }

  @Test
  void parseFilterScopesByTypeList() {
    var filter =
        SseHandler.parseFilter(
            URI.create("http://x/v1/events/stream?type=spec_dispatched,agent_session_started"));
    assertTrue(filter.test(Event.of("p", null, "spec_dispatched", "a", "h")));
    assertTrue(filter.test(Event.of("p", null, "agent_session_started", "a", "h")));
    assertFalse(filter.test(Event.of("p", null, "snapshot_created", "a", "h")));
  }

  @Test
  void parseFilterCombinesProjectAndType() {
    var filter =
        SseHandler.parseFilter(
            URI.create("http://x/v1/events/stream?project=light&type=spec_dispatched"));
    assertTrue(filter.test(Event.of("light", null, "spec_dispatched", "a", "h")));
    assertFalse(filter.test(Event.of("light", null, "snapshot_created", "a", "h")));
    assertFalse(filter.test(Event.of("dark", null, "spec_dispatched", "a", "h")));
  }

  @Test
  void parseFilterIgnoresBlankParameterValues() {
    var filter = SseHandler.parseFilter(URI.create("http://x/v1/events/stream?project=&type="));
    assertTrue(filter.test(Event.of("anything", null, "anything", "a", "h")));
  }
}
