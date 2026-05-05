package ai.singlr.sing.commands;

import ai.singlr.sing.config.SingYaml;
import ai.singlr.sing.config.YamlUtil;
import ai.singlr.sing.engine.Banner;
import ai.singlr.sing.engine.ContainerExec;
import ai.singlr.sing.engine.ContainerManager;
import ai.singlr.sing.engine.ContainerState;
import ai.singlr.sing.engine.GitSpecSync;
import ai.singlr.sing.engine.NameValidator;
import ai.singlr.sing.engine.ShellExec;
import ai.singlr.sing.engine.ShellExecutor;
import ai.singlr.sing.engine.SingPaths;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Function;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
    name = "sync",
    description = "Synchronize project specs through Git.",
    mixinStandardHelpOptions = true)
public final class SpecSyncCommand implements Runnable {

  private final Function<Boolean, ShellExec> shellFactory;

  public SpecSyncCommand() {
    this(ShellExecutor::new);
  }

  SpecSyncCommand(Function<Boolean, ShellExec> shellFactory) {
    this.shellFactory = shellFactory;
  }

  @Parameters(index = "0", description = "Project name.")
  private String name;

  @Parameters(
      index = "1",
      arity = "0..1",
      description = "Operation: status, pull, push, init.",
      defaultValue = "status")
  private String operation;

  @Option(
      names = {"-f", "--file"},
      description = "Path to sail.yaml project descriptor.",
      defaultValue = "sail.yaml")
  private String file;

  @Option(names = "--remote", description = "Remote URL to add when initializing specs Git.")
  private String remote;

  @Option(names = "--branch", description = "Initial branch for specs Git.", defaultValue = "main")
  private String branch;

  @Option(names = "--json", description = "Output in JSON format.")
  private boolean json;

  @Option(names = "--dry-run", description = "Print commands instead of executing them.")
  private boolean dryRun;

  @Spec private CommandSpec commandSpec;

  @Override
  public void run() {
    CliCommand.run(commandSpec, this::execute);
  }

  private void execute() throws Exception {
    NameValidator.requireValidProjectName(name);
    NameValidator.requireValidGitRef(branch, "branch");
    var action = SyncOperation.parse(operation);
    var sync = sync();
    var output =
        switch (action) {
          case STATUS -> SyncOutput.status(name, sync.status());
          case PULL -> SyncOutput.result(name, sync.pull());
          case PUSH -> SyncOutput.result(name, sync.push());
          case INIT -> SyncOutput.result(name, sync.init(remote, branch));
        };

    if (json) {
      System.out.println(CliJson.stringify(output));
      return;
    }

    printHuman(output);
  }

  private GitSpecSync sync() throws Exception {
    var shell = shellFactory.apply(dryRun);
    var state = new ContainerManager(shell).queryState(name);
    switch (state) {
      case ContainerState.Running ignored -> {}
      case ContainerState.Stopped ignored ->
          throw new IllegalStateException(
              "Project '" + name + "' is stopped. Start it with: sail project start " + name);
      case ContainerState.NotCreated ignored ->
          throw new IllegalStateException(
              "Project '" + name + "' does not exist. Run 'sail project create' first.");
      case ContainerState.Error error ->
          throw new IllegalStateException("Container error: " + error.message());
    }

    var singYamlPath = SingPaths.resolveSingYaml(name, file);
    if (!Files.exists(singYamlPath)) {
      throw new IllegalStateException(
          "Project descriptor not found: " + singYamlPath.toAbsolutePath());
    }
    var config = SingYaml.fromMap(YamlUtil.parseFile(singYamlPath));
    if (config.agent() == null || config.agent().specsDir() == null) {
      throw new IllegalStateException(
          "No specs_dir configured in agent block. Add it to sail.yaml.");
    }
    NameValidator.requireSafePath(config.agent().specsDir(), "agent.specs_dir");
    NameValidator.requireValidSshUser(config.sshUser());

    var specsDir = "/home/" + config.sshUser() + "/workspace/" + config.agent().specsDir();
    return new GitSpecSync(shell, ContainerExec.asDevUser(name, List.of("git", "-C", specsDir)));
  }

  private static void printHuman(SyncOutput output) {
    Banner.printBranding(System.out, Ansi.AUTO);
    var ansi = Ansi.AUTO;
    var status = output.status();
    var color =
        switch (status.state()) {
          case CLEAN -> "green";
          case BEHIND, AHEAD -> "yellow";
          case DIRTY, DIVERGED, CONFLICTED, NO_UPSTREAM, NOT_A_REPOSITORY, ERROR -> "red";
        };
    System.out.println(
        ansi.string(
            "  @|bold Spec sync:|@ "
                + output.name()
                + "  @|"
                + color
                + " "
                + status.state().name().toLowerCase()
                + "|@"));
    System.out.println(ansi.string("  @|faint " + status.message() + "|@"));
    if (status.branch() != null) {
      System.out.println("  Branch: " + status.branch());
    }
    if (status.upstream() != null) {
      System.out.println("  Upstream: " + status.upstream());
    }
    if (status.ahead() > 0 || status.behind() > 0) {
      System.out.println("  Ahead: " + status.ahead() + "  Behind: " + status.behind());
    }
    if (output.operation() != null) {
      System.out.println(
          "  Operation: " + output.operation() + (output.changed() ? " changed" : " unchanged"));
    }
  }

  private enum SyncOperation {
    STATUS,
    PULL,
    PUSH,
    INIT;

    private static SyncOperation parse(String value) {
      return switch (value == null ? "status" : value.toLowerCase()) {
        case "status" -> STATUS;
        case "pull" -> PULL;
        case "push" -> PUSH;
        case "init" -> INIT;
        default ->
            throw new IllegalArgumentException(
                "Unknown spec sync operation: " + value + ". Use status, pull, push, or init.");
      };
    }
  }

  private record SyncOutput(
      String name,
      String operation,
      boolean changed,
      String message,
      GitSpecSync.Status status,
      GitSpecSync.Status before) {
    private static SyncOutput status(String name, GitSpecSync.Status status) {
      return new SyncOutput(name, null, false, status.message(), status, null);
    }

    private static SyncOutput result(String name, GitSpecSync.OperationResult result) {
      return new SyncOutput(
          name,
          result.operation(),
          result.changed(),
          result.message(),
          result.after(),
          result.before());
    }
  }
}
