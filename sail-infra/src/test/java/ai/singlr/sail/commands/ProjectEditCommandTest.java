/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.commands;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProjectEditCommandTest {

  @Test
  void acceptsAValidDescriptorWhoseNameMatches() {
    assertDoesNotThrow(
        () -> ProjectEditCommand.validate("acme", "name: acme\ndescription: edited\n"));
  }

  @Test
  void rejectsRenamingTheProject() {
    var error =
        assertThrows(
            IllegalArgumentException.class,
            () -> ProjectEditCommand.validate("acme", "name: renamed\n"));
    assertTrue(error.getMessage().contains("must stay 'acme'"));
  }

  @Test
  void rejectsADescriptorWithNoName() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ProjectEditCommand.validate("acme", "description: nameless\n"));
  }

  @Test
  void rejectsMalformedYaml() {
    var error =
        assertThrows(
            IllegalArgumentException.class,
            () -> ProjectEditCommand.validate("acme", "name: acme\nrepos: [unclosed"));
    assertTrue(error.getMessage().contains("not valid YAML"));
  }
}
