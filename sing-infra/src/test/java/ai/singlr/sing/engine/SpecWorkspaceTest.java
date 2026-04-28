/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import static org.junit.jupiter.api.Assertions.*;

import ai.singlr.sing.config.Spec;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpecWorkspaceTest {

  private static final String INDEX_YAML =
      """
      specs:
        - id: oauth-flow
          title: OAuth Flow
          status: pending
        - id: search-api
          title: Search API
          status: review
          depends_on:
            - oauth-flow
      """;

  @Test
  void readIndexParsesOrderedSpecs() throws Exception {
    var shell =
        new ScriptedShellExecutor().onOk("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML);
    var workspace = new SpecWorkspace(shell, "acme-health", "/home/dev/workspace/specs");

    var specs = workspace.readIndex();

    assertEquals(2, specs.size());
    assertEquals("oauth-flow", specs.getFirst().id());
    assertEquals("search-api", specs.get(1).id());
  }

  @Test
  void readSpecBodyReturnsNullWhenMissing() throws Exception {
    var shell =
        new ScriptedShellExecutor()
            .onFail("cat /home/dev/workspace/specs/oauth-flow/spec.md", "No such file");
    var workspace = new SpecWorkspace(shell, "acme-health", "/home/dev/workspace/specs");

    var content = workspace.readSpecBody("oauth-flow");

    assertNull(content);
  }

  @Test
  void readIndexThrowsOnUnexpectedFailure() {
    var shell =
        new ScriptedShellExecutor()
            .onFail("cat /home/dev/workspace/specs/index.yaml", "permission denied");
    var workspace = new SpecWorkspace(shell, "acme-health", "/home/dev/workspace/specs");

    var error = assertThrows(java.io.IOException.class, workspace::readIndex);

    assertTrue(error.getMessage().contains("Failed to read spec index"));
  }

  @Test
  void readSpecBodyThrowsOnUnexpectedFailure() {
    var shell =
        new ScriptedShellExecutor()
            .onFail("cat /home/dev/workspace/specs/oauth-flow/spec.md", "permission denied");
    var workspace = new SpecWorkspace(shell, "acme-health", "/home/dev/workspace/specs");

    var error = assertThrows(java.io.IOException.class, () -> workspace.readSpecBody("oauth-flow"));

    assertTrue(error.getMessage().contains("Failed to read spec markdown"));
  }

  @Test
  void createSpecWritesMarkdownAndIndex() throws Exception {
    var shell =
        new ScriptedShellExecutor(new ShellExec.Result(0, "", ""))
            .onceOnFail("test -e /home/dev/workspace/specs/oauth-flow", "No such file");
    var workspace = new SpecWorkspace(shell, "acme-health", "/home/dev/workspace/specs");
    var spec = new Spec("oauth-flow", "OAuth Flow", "pending", null, List.of(), null);

    workspace.createSpec(spec, "# OAuth Flow");

    var commands = shell.invocations();
    assertTrue(
        commands.stream()
            .anyMatch(cmd -> cmd.contains("mkdir -p /home/dev/workspace/specs/oauth-flow")));
    assertTrue(
        commands.stream()
            .anyMatch(cmd -> cmd.contains("/home/dev/workspace/specs/oauth-flow/spec.md")));
    assertTrue(
        commands.stream().anyMatch(cmd -> cmd.contains("/home/dev/workspace/specs/index.yaml")));
    assertTrue(commands.stream().anyMatch(cmd -> cmd.contains("oauth-flow")));
  }

  @Test
  void createSpecRejectsDuplicateIndexEntry() {
    var shell =
        new ScriptedShellExecutor(new ShellExec.Result(0, "", ""))
            .onOk("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML);
    var workspace = new SpecWorkspace(shell, "acme-health", "/home/dev/workspace/specs");
    var spec = new Spec("oauth-flow", "OAuth Flow", "pending", null, List.of(), null);

    var error =
        assertThrows(
            IllegalArgumentException.class, () -> workspace.createSpec(spec, "# OAuth Flow"));

    assertTrue(error.getMessage().contains("already exists"));
  }

  @Test
  void writeIndexKeepsPathOutOfShellScript() throws Exception {
    var shell = new ScriptedShellExecutor(new ShellExec.Result(0, "", ""));
    var workspace = new SpecWorkspace(shell, "acme-health", "/home/dev/specs; touch /tmp/pwned");
    var spec = new Spec("oauth-flow", "OAuth Flow", "pending", null, List.of(), null);

    workspace.writeIndex(List.of(spec));

    assertTrue(shell.invocations().getLast().contains("printf '%s' \"$1\" > \"$2\""));
  }

  @Test
  void updateStatusWritesUpdatedIndex() throws Exception {
    var shell =
        new ScriptedShellExecutor(new ShellExec.Result(0, "", ""))
            .onOk("cat /home/dev/workspace/specs/index.yaml", INDEX_YAML);
    var workspace = new SpecWorkspace(shell, "acme-health", "/home/dev/workspace/specs");

    var updated = workspace.updateStatus("oauth-flow", "review");

    assertEquals("review", updated.status());
    assertTrue(shell.invocations().stream().anyMatch(cmd -> cmd.contains("status: review")));
  }
}
