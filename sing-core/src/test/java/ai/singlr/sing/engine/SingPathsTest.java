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

  private static final Path HOME = Path.of(System.getProperty("user.home"));

  @Test
  void singDirIsUnderHome() {
    var path = SingPaths.singDir();

    assertEquals(HOME.resolve(".sing"), path);
  }

  @Test
  void projectDirIsUnderSingDir() {
    var path = SingPaths.projectDir("acme-health");

    assertEquals(HOME.resolve(".sing/projects/acme-health"), path);
  }

  @Test
  void provisionStateIsInsideProjectDir() {
    var projDir = SingPaths.projectDir("test");
    var stateFile = SingPaths.provisionState("test");

    assertTrue(stateFile.startsWith(projDir));
    assertTrue(stateFile.toString().endsWith("provision-state.yaml"));
  }

  @Test
  void projectDirVariesByName() {
    assertNotEquals(SingPaths.projectDir("alpha"), SingPaths.projectDir("beta"));
  }

  @Test
  void hostConfigPathReturnsNewLocationByDefault() {
    var path = SingPaths.hostConfigWritePath();

    assertEquals(HOME.resolve(".sing/host.yaml"), path);
  }

  @Test
  void legacyHostConfigPathReturnsEtcSing() {
    var path = SingPaths.legacyHostConfigPath();

    assertEquals(Path.of("/etc/sing/host.yaml"), path);
  }

  @Test
  void clientConfigPathIsUnderSingDir() {
    var path = SingPaths.clientConfigPath();

    assertEquals(HOME.resolve(".sing/config.yaml"), path);
  }

  @Test
  void updateCheckFileIsUnderSingDir() {
    var path = SingPaths.updateCheckFile();

    assertEquals(HOME.resolve(".sing/update-check.yaml"), path);
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
