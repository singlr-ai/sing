/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ContextRepoGeneratorTest {

  @Test
  void generateFiles_producesScaffoldUnderContextDirectory() {
    var files = ContextRepoGenerator.generateFiles("/home/dev/workspace/", "acme-api");

    assertEquals(4, files.size());
    assertEquals("/home/dev/workspace/.context/system/README.md", files.get(0).remotePath());
    assertEquals("/home/dev/workspace/.context/patterns/README.md", files.get(1).remotePath());
    assertEquals("/home/dev/workspace/.context/failures/README.md", files.get(2).remotePath());
    assertEquals("/home/dev/workspace/.context/.gitignore", files.get(3).remotePath());
  }

  @Test
  void generateFiles_systemReadmeIncludesProjectName() {
    var files = ContextRepoGenerator.generateFiles("/home/dev/workspace/", "acme-api");

    assertTrue(files.get(0).content().contains("acme-api"));
    assertTrue(files.get(0).content().contains("Project Context"));
  }

  @Test
  void generateFiles_allFilesAreNotExecutable() {
    var files = ContextRepoGenerator.generateFiles("/home/dev/workspace/", "acme-api");

    for (var file : files) {
      assertFalse(file.executable());
    }
  }

  @Test
  void generateContextInstructions_mentionsContextDirectory() {
    var instructions = ContextRepoGenerator.generateContextInstructions();

    assertTrue(instructions.contains(".context/"));
    assertTrue(instructions.contains("system/README.md"));
    assertTrue(instructions.contains("patterns/"));
    assertTrue(instructions.contains("failures/"));
  }

  @Test
  void generateContextInstructions_includesReadAtStartRule() {
    var instructions = ContextRepoGenerator.generateContextInstructions();

    assertTrue(instructions.contains("Read `system/README.md` at the start of every session"));
  }

  @Test
  void generateFiles_failuresReadmeIncludesEntryFormat() {
    var files = ContextRepoGenerator.generateFiles("/home/dev/workspace/", "test-project");

    var failuresContent = files.get(2).content();
    assertTrue(failuresContent.contains("Symptom"));
    assertTrue(failuresContent.contains("Root cause"));
    assertTrue(failuresContent.contains("Fix"));
  }
}
