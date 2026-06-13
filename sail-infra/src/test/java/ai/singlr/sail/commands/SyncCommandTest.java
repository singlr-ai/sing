/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.commands;

import static org.junit.jupiter.api.Assertions.*;

import ai.singlr.sail.api.Event;
import ai.singlr.sail.config.SyncConfig;
import ai.singlr.sail.sync.SyncEngine;
import org.junit.jupiter.api.Test;

class SyncCommandTest {

  @Test
  void resolveMainPrefersTheExplicitFlag() {
    assertEquals(
        "sail@override",
        SyncCommand.resolveMain("sail@override", new SyncConfig(SyncConfig.ROLE_NODE, "sail@cfg")));
  }

  @Test
  void resolveMainFallsBackToTheConfiguredMain() {
    assertEquals(
        "sail@maindevbox",
        SyncCommand.resolveMain(null, new SyncConfig(SyncConfig.ROLE_NODE, "sail@maindevbox")));
  }

  @Test
  void resolveMainRefusesWhenThisBoxIsMain() {
    var error =
        assertThrows(
            IllegalStateException.class,
            () -> SyncCommand.resolveMain(null, new SyncConfig(SyncConfig.ROLE_MAIN, null)));
    assertTrue(error.getMessage().contains("main devbox"));
  }

  @Test
  void resolveMainRefusesWhenNothingIsConfigured() {
    var error =
        assertThrows(
            IllegalStateException.class, () -> SyncCommand.resolveMain("  ", SyncConfig.unset()));
    assertTrue(error.getMessage().contains("No main devbox configured"));
  }

  @Test
  void rendersJsonReport() {
    var json = SyncCommand.render(new SyncEngine.Report(1, 2, 3, 4), true);
    assertEquals("{\"pulled\": 1, \"pushed\": 2, \"merged\": 3, \"conflicts\": 4}", json);
  }

  @Test
  void rendersConvergedHumanReport() {
    var text = SyncCommand.render(new SyncEngine.Report(0, 0, 0, 0), false);
    assertTrue(text.contains("Already in sync"));
  }

  @Test
  void rendersChangesWithoutConflicts() {
    var text = SyncCommand.render(new SyncEngine.Report(2, 1, 0, 0), false);
    assertTrue(text.contains("pulled"));
    assertFalse(text.contains("conflict"));
  }

  @Test
  void rendersConflictGuidanceWhenConflictsExist() {
    var text = SyncCommand.render(new SyncEngine.Report(0, 0, 0, 2), false);
    assertTrue(text.contains("2 conflict(s) need your decision"));
    assertTrue(text.contains("sail conflicts"));
  }

  @Test
  void notifiesOnlyWhenAroundBringsRemoteWorkOrAConflict() {
    assertTrue(SyncCommand.shouldNotify(new SyncEngine.Report(1, 0, 0, 0)));
    assertTrue(SyncCommand.shouldNotify(new SyncEngine.Report(0, 0, 1, 0)));
    assertTrue(SyncCommand.shouldNotify(new SyncEngine.Report(0, 0, 0, 1)));
    assertFalse(
        SyncCommand.shouldNotify(new SyncEngine.Report(0, 5, 0, 0)), "a pure push is local");
    assertFalse(SyncCommand.shouldNotify(new SyncEngine.Report(0, 0, 0, 0)));
  }

  @Test
  void boardUpdatedEventCarriesTheCountsAndScope() {
    var event = SyncCommand.boardUpdatedEvent("devbox", new SyncEngine.Report(3, 1, 2, 4));

    assertEquals(Event.WellKnownTypes.BOARD_UPDATED, event.type());
    assertEquals("devbox", event.host());
    assertEquals(3, event.data().get("pulled"));
    assertEquals(2, event.data().get("merged"));
    assertEquals(4, event.data().get("conflicts"));
  }
}
