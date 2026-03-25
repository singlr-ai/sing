/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SingPathsTest {

  @TempDir Path tempDir;

  @Test
  void projectDirReturnsExpectedPath() {
    var path = SingPaths.projectDir("acme-health");
    assertEquals(Path.of("/etc/sing/projects/acme-health"), path);
  }

  @Test
  void provisionStateReturnsExpectedPath() {
    var path = SingPaths.provisionState("acme-health");
    assertEquals(Path.of("/etc/sing/projects/acme-health/provision-state.yaml"), path);
  }

  @Test
  void projectDirVariesByName() {
    assertNotEquals(SingPaths.projectDir("alpha"), SingPaths.projectDir("beta"));
  }

  @Test
  void provisionStateIsInsideProjectDir() {
    var projDir = SingPaths.projectDir("test");
    var stateFile = SingPaths.provisionState("test");
    assertTrue(stateFile.startsWith(projDir));
  }

  @Test
  void resolveSingYaml_existingFileReturnsIt() throws Exception {
    var yamlFile = tempDir.resolve("sing.yaml");
    Files.writeString(yamlFile, "name: test");

    var result = SingPaths.resolveSingYaml("test", yamlFile.toString());

    assertEquals(yamlFile, result);
  }

  @Test
  void resolveSingYaml_fallsBackToNamedDir() throws Exception {
    var projectDir = tempDir.resolve("my-project");
    Files.createDirectories(projectDir);
    var namedYaml = projectDir.resolve("sing.yaml");
    Files.writeString(namedYaml, "name: my-project");

    var result =
        SingPaths.resolveSingYaml(
            projectDir.toString(), tempDir.resolve("nonexistent.yaml").toString());

    assertEquals(namedYaml, result);
  }

  @Test
  void resolveSingYaml_returnsOriginalWhenNothingExists() {
    var result = SingPaths.resolveSingYaml("whatever", "/does/not/exist.yaml");

    assertEquals(Path.of("/does/not/exist.yaml"), result);
  }

  @Test
  void resolveSingYaml_nullNameReturnsFilePath() {
    var result = SingPaths.resolveSingYaml(null, "sing.yaml");

    assertEquals(Path.of("sing.yaml"), result);
  }
}
