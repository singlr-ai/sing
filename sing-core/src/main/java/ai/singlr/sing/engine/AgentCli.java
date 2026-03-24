/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

/**
 * Known AI coding agent CLIs that can be installed inside a project container. Each constant
 * carries the metadata needed to install and invoke the agent: the YAML config name, the binary
 * name on PATH, the installation method, and the shell command to install it.
 */
public enum AgentCli {
  CLAUDE_CODE(
      "claude-code",
      "claude",
      InstallMethod.NATIVE_SCRIPT,
      "curl -fsSL https://claude.ai/install.sh | bash",
      "CLAUDE.md"),
  CODEX("codex", "codex", InstallMethod.NPM, "sudo npm install -g @openai/codex", "AGENTS.md"),
  GEMINI(
      "gemini", "gemini", InstallMethod.NPM, "sudo npm install -g @google/gemini-cli", "GEMINI.md");

  /** How the agent CLI is installed. */
  public enum InstallMethod {
    /** Native install script (no Node.js dependency). */
    NATIVE_SCRIPT,
    /** Global npm package (requires Node.js). */
    NPM
  }

  private final String yamlName;
  private final String binaryName;
  private final InstallMethod method;
  private final String installCommand;
  private final String contextFileName;

  AgentCli(
      String yamlName,
      String binaryName,
      InstallMethod method,
      String installCommand,
      String contextFileName) {
    this.yamlName = yamlName;
    this.binaryName = binaryName;
    this.method = method;
    this.installCommand = installCommand;
    this.contextFileName = contextFileName;
  }

  /** The name used in sing.yaml ({@code "claude-code"}, {@code "codex"}, {@code "gemini"}). */
  public String yamlName() {
    return yamlName;
  }

  /** The CLI binary name on PATH ({@code "claude"}, {@code "codex"}, {@code "gemini"}). */
  public String binaryName() {
    return binaryName;
  }

  /** The installation method for this agent CLI. */
  public InstallMethod method() {
    return method;
  }

  /** The shell command to install this agent CLI. */
  public String installCommand() {
    return installCommand;
  }

  /** The context file name for this agent (e.g., "CLAUDE.md", "AGENTS.md", "GEMINI.md"). */
  public String contextFileName() {
    return contextFileName;
  }

  /** Whether this agent CLI requires Node.js to install (npm-based). */
  public boolean requiresNode() {
    return method == InstallMethod.NPM;
  }

  /**
   * Returns the shell command fragment for headless (non-interactive) task execution. The task is
   * read from the given file path inside the container via {@code $(cat ...)}.
   *
   * @param taskFile absolute path to the task file inside the container
   * @param fullPermissions whether to auto-approve all actions
   */
  public String headlessCommand(String taskFile, boolean fullPermissions) {
    var task = "\"$(cat " + taskFile + ")\"";
    return switch (this) {
      case CLAUDE_CODE -> {
        var perm = fullPermissions ? " --dangerously-skip-permissions" : "";
        yield binaryName + " --print" + perm + " -p " + task;
      }
      case CODEX -> {
        var perm = fullPermissions ? " --full-auto" : "";
        yield binaryName + " exec" + perm + " " + task;
      }
      case GEMINI -> {
        var perm = fullPermissions ? " --yolo" : "";
        yield binaryName + perm + " -p " + task;
      }
    };
  }

  /**
   * Returns the shell command fragment for interactive (TTY) agent launch.
   *
   * @param fullPermissions whether to auto-approve all actions
   */
  public String interactiveCommand(boolean fullPermissions) {
    return switch (this) {
      case CLAUDE_CODE ->
          fullPermissions ? binaryName + " --dangerously-skip-permissions" : binaryName;
      case CODEX -> fullPermissions ? binaryName + " --full-auto" : binaryName;
      case GEMINI -> fullPermissions ? binaryName + " --yolo" : binaryName;
    };
  }

  /**
   * Looks up an {@code AgentCli} by its YAML name.
   *
   * @throws IllegalArgumentException if the name is not a known agent CLI
   */
  public static AgentCli fromYamlName(String name) {
    for (var cli : values()) {
      if (cli.yamlName.equals(name)) {
        return cli;
      }
    }
    throw new IllegalArgumentException(
        "Unknown agent CLI: '"
            + name
            + "'. Known agents: claude-code, codex, gemini."
            + "\n  Check the 'install' list in your sing.yaml agent section.");
  }
}
