/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import ai.singlr.sing.config.SingYaml;
import ai.singlr.sing.engine.AgentCli;
import ai.singlr.sing.engine.ProjectDefaults;
import java.util.List;

/**
 * Detects when selected agent CLIs require Node.js but Node is not in the project runtimes, and
 * resolves the mismatch interactively before provisioning begins.
 */
final class NodeDependencyCheck {

  sealed interface Resolution {
    record Unchanged(SingYaml config) implements Resolution {}

    record NodeAdded(SingYaml config) implements Resolution {}

    record AgentsDropped(SingYaml config, List<AgentCli> dropped) implements Resolution {}

    record Aborted() implements Resolution {}
  }

  private NodeDependencyCheck() {}

  static List<AgentCli> findNodeDependentAgents(SingYaml config) {
    if (config.agent() == null) {
      return List.of();
    }
    var installNames = config.agent().install();
    if (installNames == null || installNames.isEmpty()) {
      installNames = List.of(config.agent().type());
    }
    return installNames.stream()
        .map(AgentCli::fromYamlName)
        .filter(AgentCli::requiresNode)
        .toList();
  }

  static boolean hasNodeRuntime(SingYaml config) {
    return config.runtimes() != null && config.runtimes().node() != null;
  }

  static Resolution resolve(SingYaml config, boolean autoAccept) {
    var nodeAgents = findNodeDependentAgents(config);
    if (nodeAgents.isEmpty() || hasNodeRuntime(config)) {
      return new Resolution.Unchanged(config);
    }

    if (autoAccept) {
      var updated = config.withNodeRuntime(ProjectDefaults.DEFAULT_NODE_VERSION);
      System.out.println(
          "  Node.js "
              + ProjectDefaults.DEFAULT_NODE_VERSION
              + " added automatically (required by "
              + formatAgentNames(nodeAgents)
              + ").");
      return new Resolution.NodeAdded(updated);
    }

    System.out.println();
    System.out.println("  The following agent CLIs require Node.js:");
    for (var agent : nodeAgents) {
      System.out.println("    - " + agent.displayName());
    }
    System.out.println();
    System.out.println("  Node.js is not in the selected runtimes for this project.");

    if (ConsoleHelper.confirm(
        "Add Node.js " + ProjectDefaults.DEFAULT_NODE_VERSION + " to this project?")) {
      var updated = config.withNodeRuntime(ProjectDefaults.DEFAULT_NODE_VERSION);
      return new Resolution.NodeAdded(updated);
    }

    var droppedNames = nodeAgents.stream().map(AgentCli::displayName).toList();
    System.out.println(
        "  The following agents will be skipped: " + String.join(", ", droppedNames) + ".");

    if (!ConsoleHelper.confirmNo("Continue without them?")) {
      return new Resolution.Aborted();
    }

    var nodeAgentYamlNames = nodeAgents.stream().map(AgentCli::yamlName).toList();
    var allInstall = resolveInstallList(config);
    var filteredInstall = allInstall.stream().filter(n -> !nodeAgentYamlNames.contains(n)).toList();
    var updated = config.withAgentInstall(filteredInstall);
    return new Resolution.AgentsDropped(updated, nodeAgents);
  }

  static void failNonInteractive(SingYaml config) {
    var nodeAgents = findNodeDependentAgents(config);
    if (nodeAgents.isEmpty() || hasNodeRuntime(config)) {
      return;
    }
    var agentNames = formatAgentNames(nodeAgents);
    throw new IllegalStateException(
        agentNames
            + " require(s) Node.js, but Node is not in the project runtimes."
            + "\n  Either add 'node: "
            + ProjectDefaults.DEFAULT_NODE_VERSION
            + "' under 'runtimes:' in sing.yaml,"
            + "\n  or remove the Node-dependent agent(s) from 'agent.install'.");
  }

  private static List<String> resolveInstallList(SingYaml config) {
    if (config.agent() == null) {
      return List.of();
    }
    var install = config.agent().install();
    if (install == null || install.isEmpty()) {
      return List.of(config.agent().type());
    }
    return install;
  }

  private static String formatAgentNames(List<AgentCli> agents) {
    return String.join(", ", agents.stream().map(AgentCli::displayName).toList());
  }
}
