/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
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

  private static final Path HOME = Path.of(System.getProperty("user.home"));

  @Test
  void singDirIsUnderHome() {
    assertEquals(HOME.resolve(".sing"), SingPaths.singDir());
  }

  @Test
  void projectDirIsUnderSingDir() {
    assertEquals(HOME.resolve(".sing/projects/acme-health"), SingPaths.projectDir("acme-health"));
  }

  @Test
  void provisionStateIsInsideProjectDir() {
    var stateFile = SingPaths.provisionState("test");

    assertTrue(stateFile.startsWith(SingPaths.projectDir("test")));
    assertTrue(stateFile.toString().endsWith("provision-state.yaml"));
  }

  @Test
  void projectDirVariesByName() {
    assertNotEquals(SingPaths.projectDir("alpha"), SingPaths.projectDir("beta"));
  }

  @Test
  void hostConfigPathIsUnderSingDir() {
    assertEquals(HOME.resolve(".sing/host.yaml"), SingPaths.hostConfigPath());
  }

  @Test
  void clientConfigPathIsUnderSingDir() {
    assertEquals(HOME.resolve(".sing/config.yaml"), SingPaths.clientConfigPath());
  }

  @Test
  void updateCheckFileIsUnderSingDir() {
    assertEquals(HOME.resolve(".sing/update-check.yaml"), SingPaths.updateCheckFile());
  }

  @Test
  void resolveSingYamlFindsCanonicalFirst() throws Exception {
    var projectDir = HOME.resolve(".sing/projects/test-canonical");
    Files.createDirectories(projectDir);
    var canonical = projectDir.resolve("sail.yaml");
    Files.writeString(canonical, "name: test");
    try {
      var result = SingPaths.resolveSingYaml("test-canonical", "nonexistent.yaml");

      assertEquals(canonical, result);
    } finally {
      Files.deleteIfExists(canonical);
      Files.deleteIfExists(projectDir);
    }
  }

  @Test
  void resolveSingYamlFallsBackToLegacyCanonical() throws Exception {
    var projectDir = HOME.resolve(".sing/projects/test-legacy-canonical");
    Files.createDirectories(projectDir);
    var legacy = projectDir.resolve("sing.yaml");
    Files.writeString(legacy, "name: test");
    try {
      var result = SingPaths.resolveSingYaml("test-legacy-canonical", "nonexistent.yaml");

      assertEquals(legacy, result);
    } finally {
      Files.deleteIfExists(legacy);
      Files.deleteIfExists(projectDir);
    }
  }

  @Test
  void resolveSingYamlFallsBackToExplicitFile() throws Exception {
    var yamlFile = tempDir.resolve("sail.yaml");
    Files.writeString(yamlFile, "name: test");

    var result = SingPaths.resolveSingYaml("nonexistent-project", yamlFile.toString());

    assertEquals(yamlFile, result);
  }

  @Test
  void resolveSingYamlFallsBackToNamedDir() throws Exception {
    var projectDir = tempDir.resolve("my-project");
    Files.createDirectories(projectDir);
    var namedYaml = projectDir.resolve("sail.yaml");
    Files.writeString(namedYaml, "name: my-project");

    var result =
        SingPaths.resolveSingYaml(
            projectDir.toString(), tempDir.resolve("nonexistent.yaml").toString());

    assertEquals(namedYaml, result);
  }

  @Test
  void resolveSingYamlFallsBackToLegacyNamedDir() throws Exception {
    var projectDir = tempDir.resolve("my-legacy-project");
    Files.createDirectories(projectDir);
    var namedYaml = projectDir.resolve("sing.yaml");
    Files.writeString(namedYaml, "name: my-legacy-project");

    var result =
        SingPaths.resolveSingYaml(
            projectDir.toString(), tempDir.resolve("nonexistent.yaml").toString());

    assertEquals(namedYaml, result);
  }

  @Test
  void resolveSingYamlReturnsCanonicalWhenNothingExists() {
    var result = SingPaths.resolveSingYaml("whatever", "/does/not/exist.yaml");

    assertEquals(SingPaths.projectDir("whatever").resolve("sail.yaml"), result);
  }

  @Test
  void resolveSingYamlNullNameReturnsFilePath() {
    var result = SingPaths.resolveSingYaml(null, "sail.yaml");

    assertEquals(Path.of("sail.yaml"), result);
  }

  @Test
  void expandHomeExpandsTilde() {
    var result = SingPaths.expandHome("~/.ssh/id_ed25519");

    assertTrue(result.startsWith("/"));
    assertTrue(result.endsWith("/.ssh/id_ed25519"));
    assertFalse(result.startsWith("~"));
  }

  @Test
  void expandHomeReturnsAbsolutePathUnchanged() {
    assertEquals("/home/user/.ssh/id_ed25519", SingPaths.expandHome("/home/user/.ssh/id_ed25519"));
  }

  @Test
  void expandHomeReturnsNullForNull() {
    assertNull(SingPaths.expandHome(null));
  }

  @Test
  void expandHomeDoesNotExpandMidPath() {
    assertEquals("/some/~/path", SingPaths.expandHome("/some/~/path"));
  }
}
