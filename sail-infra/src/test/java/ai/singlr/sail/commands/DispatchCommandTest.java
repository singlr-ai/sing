/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.commands;

import static org.junit.jupiter.api.Assertions.*;

import ai.singlr.sail.Sail;
import ai.singlr.sail.config.SailYaml;
import ai.singlr.sail.config.Spec;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import picocli.CommandLine;

@Execution(ExecutionMode.SAME_THREAD)
class DispatchCommandTest {

  private PrintStream originalOut;
  private PrintStream originalErr;
  private ByteArrayOutputStream capturedOut;
  private ByteArrayOutputStream capturedErr;

  @BeforeEach
  void captureStreams() {
    originalOut = System.out;
    originalErr = System.err;
    capturedOut = new ByteArrayOutputStream();
    capturedErr = new ByteArrayOutputStream();
    System.setOut(new PrintStream(capturedOut));
    System.setErr(new PrintStream(capturedErr));
  }

  @AfterEach
  void restoreStreams() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  @TempDir Path tempDir;

  @Test
  void helpShowsDescription() {
    var cmd = new CommandLine(new Sail());
    var sw = new StringWriter();
    cmd.setOut(new PrintWriter(sw));

    var exitCode = cmd.execute("spec", "dispatch", "--help");

    assertEquals(0, exitCode);
    assertTrue(sw.toString().contains("Dispatch the next ready spec"));
  }

  @Test
  void failsWithMissingSailYaml() {
    var cmd = new CommandLine(new Sail());

    var exitCode =
        cmd.execute(
            "spec", "dispatch", "test-project", "-f", tempDir.resolve("nope.yaml").toString());

    assertNotEquals(0, exitCode);
  }

  @Test
  void failsGracefullyWithoutRunningContainer() throws Exception {
    var yaml =
        """
        name: test-project
        resources:
          cpu: 2
          memory: 4GB
          disk: 20GB
        agent:
          type: claude-code
          specs_dir: specs
        """;
    var yamlPath = tempDir.resolve("sail.yaml");
    Files.writeString(yamlPath, yaml);

    var cmd = new CommandLine(new Sail());

    var exitCode = cmd.execute("spec", "dispatch", "test-project", "-f", yamlPath.toString());

    assertNotEquals(0, exitCode);
  }

  @Test
  void buildTaskPromptIncludesSpecDetails() {
    var spec = new Spec("oauth-flow", "Implement OAuth", "pending", null, List.of(), null);
    var description = "Build Google OAuth integration with PKCE flow.";

    var prompt = DispatchCommand.buildTaskPrompt(spec, description, "specs");

    assertTrue(prompt.contains("oauth-flow"));
    assertTrue(prompt.contains("Implement OAuth"));
    assertTrue(prompt.contains("Build Google OAuth integration"));
    assertFalse(prompt.contains("spec.yaml"), "Prompt should not contain lifecycle instructions");
  }

  @Test
  void buildTaskPromptContainsSpecIdAndDescription() {
    var spec = new Spec("auth", "Auth", "pending", null, List.of(), null);

    var prompt = DispatchCommand.buildTaskPrompt(spec, "Details", "my-specs");

    assertTrue(prompt.contains("auth"));
    assertTrue(prompt.contains("Details"));
  }

  @Test
  void buildTaskPromptIncludesFullDescription() {
    var spec = new Spec("setup", "Setup DB", "pending", null, List.of(), null);
    var longDescription =
        """
        Create PostgreSQL schema with:
        - users table
        - sessions table
        - migrations
        """;

    var prompt = DispatchCommand.buildTaskPrompt(spec, longDescription.strip(), "specs");

    assertTrue(prompt.contains("users table"));
    assertTrue(prompt.contains("sessions table"));
    assertTrue(prompt.contains("migrations"));
  }

  @Test
  void buildTaskPromptIncludesTargetAgent() {
    var spec =
        new Spec(
            "ui",
            "Polish UI",
            "pending",
            null,
            List.of(),
            List.of("chorus"),
            "codex",
            "gpt-5.5",
            "high",
            null);

    var prompt = DispatchCommand.buildTaskPrompt(spec, "Details", "specs");

    assertTrue(prompt.contains("Target repo: chorus"));
    assertTrue(prompt.contains("Target agent: codex"));
    assertTrue(prompt.contains("Target model: gpt-5.5"));
    assertTrue(prompt.contains("Target reasoning effort: high"));
  }

  @Test
  void dispatchCommandRegisteredInSing() {
    var cmd = new CommandLine(new Sail());
    var sw = new StringWriter();
    cmd.setOut(new PrintWriter(sw));

    cmd.execute("spec", "--help");

    assertTrue(sw.toString().contains("dispatch"));
  }

  @Test
  void specOptionAccepted() {
    var cmd = new CommandLine(new Sail());
    var sw = new StringWriter();
    cmd.setOut(new PrintWriter(sw));

    var exitCode = cmd.execute("spec", "dispatch", "--help");

    assertEquals(0, exitCode);
    var help = sw.toString();
    assertTrue(help.contains("--spec"));
    assertTrue(help.contains("--background"));
    assertTrue(help.contains("--repo"));
    assertTrue(help.contains("--dry-run"));
    assertTrue(help.contains("--json"));
  }

  @Test
  void branchRepoDirUsesSingleTargetWorkDirDirectly() {
    var repo = new SailYaml.Repo("https://github.com/org/chorus.git", "chorus", null);

    var repoDir = DispatchCommand.branchRepoDir("/home/dev/workspace/chorus", List.of(repo), repo);

    assertEquals("/home/dev/workspace/chorus", repoDir);
  }

  @Test
  void branchRepoDirAppendsRepoPathForMultiRepoDispatch() {
    var sing = new SailYaml.Repo("https://github.com/org/sing.git", "sing", null);
    var chorus = new SailYaml.Repo("https://github.com/org/chorus.git", "chorus", null);

    var repoDir =
        DispatchCommand.branchRepoDir("/home/dev/workspace", List.of(sing, chorus), chorus);

    assertEquals("/home/dev/workspace/chorus", repoDir);
  }
}
