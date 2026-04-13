/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import ai.singlr.sing.config.HostYaml;
import ai.singlr.sing.config.SingYaml;
import ai.singlr.sing.config.YamlUtil;
import ai.singlr.sing.engine.Banner;
import ai.singlr.sing.engine.ContainerExec;
import ai.singlr.sing.engine.ContainerManager;
import ai.singlr.sing.engine.ContainerState;
import ai.singlr.sing.engine.NameValidator;
import ai.singlr.sing.engine.ProjectPhase;
import ai.singlr.sing.engine.ProjectProvisioner;
import ai.singlr.sing.engine.ProvisionListener;
import ai.singlr.sing.engine.ProvisionTracker;
import ai.singlr.sing.engine.ShellExecutor;
import ai.singlr.sing.engine.SingPaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
    name = "create",
    description = "Create a project environment from sing.yaml.",
    mixinStandardHelpOptions = true)
public final class ProjectCreateCommand implements Runnable {

  @Parameters(
      index = "0",
      arity = "0..1",
      description = "Project name (resolves to <name>/sing.yaml if -f not given).")
  private String name;

  @Option(names = "--dry-run", description = "Print commands instead of executing them.")
  private boolean dryRun;

  @Option(
      names = {"-f", "--file"},
      description = "Path to sing.yaml project descriptor.")
  private String file;

  @Option(names = "--json", description = "Output in JSON format.")
  private boolean json;

  @Option(names = "--yes", description = "Skip confirmation prompts (for non-interactive use).")
  private boolean yes;

  @Option(names = "--git-token", description = "Access token for cloning private repos over HTTPS.")
  private String gitToken;

  @Spec private CommandSpec spec;

  @Override
  public void run() {
    try {
      execute();
    } catch (Exception e) {
      var msg = Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName());
      System.err.println(Banner.errorLine(msg, Ansi.AUTO));
      System.err.println(
          "  If this is unexpected, re-run with --dry-run to see what would execute.");
      throw new picocli.CommandLine.ExecutionException(spec.commandLine(), msg, e);
    }
  }

  private void execute() throws Exception {
    if (!json) {
      Banner.printBranding(System.out, Ansi.AUTO);
    }

    if (!dryRun && !ConsoleHelper.isRoot()) {
      throw new IllegalStateException(
          "Root privileges required. Run with: sudo sing project create");
    }

    var singYamlPath = resolveSingYamlPath();
    if (!Files.exists(singYamlPath)) {
      var hint = new StringBuilder();
      hint.append("Project descriptor not found: ").append(singYamlPath.toAbsolutePath());
      if (name != null) {
        hint.append("\n  Run 'sing project init' to create ")
            .append(name)
            .append("/sing.yaml, or specify one with --file.");
      } else {
        hint.append(
            "\n  Run 'sing project init' to generate a sing.yaml, or specify one with --file.");
      }
      throw new IllegalStateException(hint.toString());
    }

    SingYaml config = SingYaml.fromMap(YamlUtil.parseFile(singYamlPath));

    if (config.name() == null || config.name().isBlank()) {
      throw new IllegalStateException("sing.yaml must have a 'name' field.");
    }
    NameValidator.requireValidProjectName(config.name());
    if (config.resources() == null) {
      throw new IllegalStateException(
          "sing.yaml must have a 'resources' section with cpu, memory, and disk.");
    }

    if (yes || json) {
      config = resolveNodeDependencyAuto(config);
    } else if (!dryRun) {
      config = resolveNodeDependencyInteractive(config);
    }

    var projectDir = SingPaths.projectDir(config.name());
    Files.createDirectories(projectDir);
    var canonicalYaml = projectDir.resolve("sing.yaml");
    if (!singYamlPath.toAbsolutePath().equals(canonicalYaml.toAbsolutePath())) {
      Files.copy(singYamlPath, canonicalYaml, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    var hostYamlPath = SingPaths.hostConfigPath();
    if (!Files.exists(hostYamlPath)) {
      throw new IllegalStateException("Server not initialized. Run 'sing host init' first.");
    }
    var hostYaml = HostYaml.fromMap(YamlUtil.parseFile(hostYamlPath));

    var shell = new ShellExecutor(dryRun);

    var stateFile = SingPaths.provisionState(config.name());
    var tracker = new ProvisionTracker<>(ProjectPhase.class, stateFile, dryRun);
    tracker.load();

    if (tracker.hasIncompleteRun()) {
      var mgr = new ContainerManager(shell);
      var containerState = mgr.queryState(config.name());
      if (containerState instanceof ContainerState.NotCreated) {
        if (!json) {
          System.out.println(
              Ansi.AUTO.string(
                  "  @|faint Stale state detected — container '"
                      + config.name()
                      + "' no longer exists. Starting fresh.|@"));
        }
        tracker.reset();
      }
    }

    if (tracker.hasIncompleteRun()) {
      if (!json) {
        Banner.printResumeInfo(tracker.currentState(), System.out, Ansi.AUTO);
      }
      if (!yes && !json && !ConsoleHelper.confirm("Resume provisioning?")) {
        System.out.println("  Aborted.");
        return;
      }
    }

    if (!json) {
      Banner.printProjectSummary(config, System.out, Ansi.AUTO);
    }

    if (!yes && !dryRun && !json && !tracker.hasIncompleteRun()) {
      if (!ConsoleHelper.confirm("Create project " + config.name() + "?")) {
        System.out.println("  Aborted.");
        return;
      }
    }

    var gitTokens = resolveGitTokens(config);

    var listener = json ? ProvisionListener.NOOP : ConsoleProvisionListener.INSTANCE;
    var provisioner = new ProjectProvisioner(shell, tracker, listener);
    provisioner.provision(config, hostYaml, gitTokens, singYamlPath);

    if (json) {
      var map = new LinkedHashMap<String, Object>();
      map.put("name", config.name());
      map.put("status", "created");
      System.out.println(YamlUtil.dumpJson(map));
      return;
    }

    System.out.println();
    Banner.printProjectCreated(
        config.name(), config.ssh() != null ? config.ssh().user() : null, System.out, Ansi.AUTO);

    var mgr = new ContainerManager(shell);
    var state = mgr.queryState(config.name());
    if (state instanceof ContainerState.Running r && r.ipv4() != null) {
      var serverIp = hostYaml.serverIp();
      var serverUser = System.getProperty("user.name");
      if (serverIp != null) {
        Banner.printSshConfig(config.name(), serverIp, serverUser, r.ipv4(), System.out, Ansi.AUTO);
      } else {
        System.out.println();
        System.out.println(
            Ansi.AUTO.string(
                "    @|yellow Server IP not configured.|@"
                    + " Run: sudo sing host config set server-ip <your-server-ip>"));
        System.out.println(
            Ansi.AUTO.string(
                "    Then: sing connect " + config.name() + " for SSH config snippet."));
      }

      var ports = ContainerExec.queryServicePorts(shell, config.name());
      if (!ports.isEmpty()) {
        Banner.printSshTunnels(
            config.name(),
            config.ssh() != null ? config.ssh().user() : null,
            ports,
            System.out,
            Ansi.AUTO);
      }
    }
  }

  private Map<String, String> resolveGitTokens(SingYaml config) {
    if (config.git() == null || !"token".equals(config.git().auth())) {
      return Map.of();
    }

    if (gitToken != null && !gitToken.isBlank()) {
      return ProjectProvisioner.singleTokenMap(gitToken);
    }

    var hosts = ProjectProvisioner.extractHttpsHosts(config.repos());
    if (hosts.isEmpty()) {
      return Map.of();
    }

    var tokens = new LinkedHashMap<String, String>();
    for (var host : hosts) {
      var envToken = ProjectProvisioner.resolveTokenForHost(host, null);
      if (envToken != null && !envToken.isBlank()) {
        tokens.put(host, envToken);
      }
    }

    var missingHosts = hosts.stream().filter(h -> !tokens.containsKey(h)).toList();
    if (missingHosts.isEmpty() || yes || json || dryRun) {
      return Map.copyOf(tokens);
    }

    for (var host : missingHosts) {
      try {
        var prompted =
            ConsoleHelper.readPassword("  Git access token for " + host + " (blank to skip): ");
        if (prompted != null && !prompted.isBlank()) {
          tokens.put(host, prompted);
        }
      } catch (EchoDisabledUnavailableException e) {
        throw new IllegalArgumentException(
            "Unable to read git access token interactively in this terminal.\n\n"
                + "Provide the token via one of:\n"
                + "  --git-token <token>\n"
                + "  GITHUB_TOKEN environment variable\n\n"
                + "Then re-run: sing project create <name>");
      }
    }

    return Map.copyOf(tokens);
  }

  private SingYaml resolveNodeDependencyAuto(SingYaml config) {
    var resolution = NodeDependencyCheck.resolve(config, true);
    return switch (resolution) {
      case NodeDependencyCheck.Resolution.NodeAdded r -> r.config();
      default -> config;
    };
  }

  private SingYaml resolveNodeDependencyInteractive(SingYaml config) {
    var resolution = NodeDependencyCheck.resolve(config, false);
    return switch (resolution) {
      case NodeDependencyCheck.Resolution.Unchanged r -> r.config();
      case NodeDependencyCheck.Resolution.NodeAdded r -> r.config();
      case NodeDependencyCheck.Resolution.AgentsDropped r -> r.config();
      case NodeDependencyCheck.Resolution.Aborted ignored -> {
        System.out.println("  Aborted.");
        throw new IllegalStateException(
            "Aborted: Node-dependent agents require Node.js in the project runtimes.");
      }
    };
  }

  /**
   * Resolves the sing.yaml path: {@code -f} wins, then {@code <name>/sing.yaml}, then {@code
   * ./sing.yaml}.
   */
  private Path resolveSingYamlPath() {
    if (file != null) {
      return Path.of(file);
    }
    if (name != null) {
      var namedPath = Path.of(name, "sing.yaml");
      if (Files.exists(namedPath)) {
        return namedPath;
      }
    }
    var cwdPath = Path.of("sing.yaml");
    if (Files.exists(cwdPath)) {
      return cwdPath;
    }
    if (name != null) {
      return Path.of(name, "sing.yaml");
    }
    return cwdPath;
  }
}
