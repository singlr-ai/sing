package ai.singlr.sing.commands;

import static org.junit.jupiter.api.Assertions.*;

import ai.singlr.sing.engine.ShellExec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class SpecSyncCommandTest {

  private static final String RUNNING_JSON =
      """
      [{"name":"acme","status":"Running","state":{}}]
      """;

  private static final String STOPPED_JSON =
      """
      [{"name":"acme","status":"Stopped","state":{}}]
      """;

  @TempDir Path tempDir;

  @Test
  void defaultConstructorCanRenderHelp() {
    var out = new ByteArrayOutputStream();
    var command = new CommandLine(new SpecSyncCommand());
    command.setOut(new PrintWriterBridge(out));

    assertEquals(0, command.execute("--help"));
    assertTrue(out.toString().contains("Synchronize project specs through Git"));
  }

  @Test
  void humanStatusUsesRedForDirtyRepository() throws Exception {
    var shell =
        cleanShell().on("status --porcelain=v1 -b", "## main...origin/main\n M spec.yaml\n");

    var result = execute(shell, "acme", "status", "-f", yaml());

    assertEquals(0, result.exitCode());
    assertTrue(result.stdout().contains("dirty"));
  }

  @Test
  void statusJsonPrintsStructuredState() throws Exception {
    var shell = cleanShell();

    var result = execute(shell, "acme", "status", "-f", yaml(), "--json");

    assertEquals(0, result.exitCode());
    assertTrue(result.stdout().contains("\"state\": \"clean\""));
    assertTrue(result.stdout().contains("\"repository\": true"));
  }

  @Test
  void statusHumanPrintsBranchAndUpstream() throws Exception {
    var shell = cleanShell();

    var result = execute(shell, "acme", "status", "-f", yaml());

    assertEquals(0, result.exitCode());
    assertTrue(result.stdout().contains("Spec sync:"));
    assertTrue(result.stdout().contains("Branch: main"));
    assertTrue(result.stdout().contains("Upstream: origin/main"));
  }

  @Test
  void pullPushAndInitOperationsReachGit() throws Exception {
    var pullShell =
        cleanShell()
            .on("status --porcelain=v1 -b", "## main...origin/main [behind 1]\n")
            .on("pull --ff-only", "Fast-forward\n");
    var pushShell =
        cleanShell()
            .on("status --porcelain=v1 -b", "## main...origin/main [ahead 1]\n")
            .on("git -C /home/dev/workspace/specs push", "pushed\n");
    var initShell =
        cleanShell()
            .on("rev-parse --is-inside-work-tree", new ShellExec.Result(1, "", "fatal"))
            .on("init --initial-branch main", "Initialized\n");

    assertEquals(0, execute(pullShell, "acme", "pull", "-f", yaml(), "--json").exitCode());
    assertTrue(pullShell.invoked("pull --ff-only"));
    assertEquals(0, execute(pushShell, "acme", "push", "-f", yaml(), "--json").exitCode());
    assertTrue(pushShell.invoked("git -C /home/dev/workspace/specs push"));
    assertEquals(0, execute(initShell, "acme", "init", "-f", yaml(), "--json").exitCode());
    assertTrue(initShell.invoked("init --initial-branch main"));
  }

  @Test
  void humanOperationPrintsAheadBehindAndOperation() throws Exception {
    var shell =
        cleanShell()
            .on("status --porcelain=v1 -b", "## main...origin/main [ahead 1]\n")
            .on("git -C /home/dev/workspace/specs push", "pushed\n");

    var result = execute(shell, "acme", "push", "-f", yaml());

    assertEquals(0, result.exitCode());
    assertTrue(result.stdout().contains("Ahead: 1  Behind: 0"));
    assertTrue(result.stdout().contains("Operation: push"));
  }

  @Test
  void rejectsInvalidOperationAndProjectStates() throws Exception {
    assertNotEquals(0, execute(cleanShell(), "acme", "merge", "-f", yaml()).exitCode());
    assertNotEquals(
        0, execute(shellWithContainer(STOPPED_JSON), "acme", "status", "-f", yaml()).exitCode());
    assertNotEquals(
        0, execute(shellWithContainer("[]"), "acme", "status", "-f", yaml()).exitCode());
    assertNotEquals(
        0,
        execute(
                shellWithContainer(new ShellExec.Result(1, "", "incus down")),
                "acme",
                "status",
                "-f",
                yaml())
            .exitCode());
  }

  @Test
  void rejectsMissingDescriptorAndMissingSpecsConfiguration() throws Exception {
    assertNotEquals(
        0,
        execute(cleanShell(), "acme", "status", "-f", tempDir.resolve("missing.yaml").toString())
            .exitCode());
    var noAgent = tempDir.resolve("no-agent.yaml");
    Files.writeString(noAgent, "name: acme\nssh:\n  user: dev\n");

    assertNotEquals(
        0, execute(cleanShell(), "acme", "status", "-f", noAgent.toString()).exitCode());
  }

  @Test
  void dryRunUsesDryRunFlag() throws Exception {
    var shell = cleanShell();

    execute(shell, "acme", "status", "-f", yaml(), "--json", "--dry-run");

    assertTrue(shell.dryRun());
  }

  private CommandResult execute(FakeShell shell, String... args) {
    var out = new ByteArrayOutputStream();
    var err = new ByteArrayOutputStream();
    var originalOut = System.out;
    var originalErr = System.err;
    try {
      System.setOut(new PrintStream(out));
      System.setErr(new PrintStream(err));
      var command = new CommandLine(new SpecSyncCommand(dryRun -> shell.withDryRun(dryRun)));
      command.setOut(new PrintWriterBridge(out));
      command.setErr(new PrintWriterBridge(err));
      var exitCode = command.execute(args);
      return new CommandResult(exitCode, out.toString(), err.toString());
    } finally {
      System.setOut(originalOut);
      System.setErr(originalErr);
    }
  }

  private String yaml() throws IOException {
    var path = tempDir.resolve("sing.yaml");
    Files.writeString(
        path,
        """
        name: acme
        ssh:
          user: dev
        agent:
          type: claude-code
          specs_dir: specs
        """);
    return path.toString();
  }

  private static FakeShell cleanShell() {
    return shellWithContainer(RUNNING_JSON)
        .on("rev-parse --is-inside-work-tree", "true\n")
        .on("branch --show-current", "main\n")
        .on("rev-parse --abbrev-ref --symbolic-full-name @{u}", "origin/main\n")
        .on("status --porcelain=v1 -b", "## main...origin/main\n");
  }

  private static FakeShell shellWithContainer(String containerJson) {
    return shellWithContainer(new ShellExec.Result(0, containerJson, ""));
  }

  private static FakeShell shellWithContainer(ShellExec.Result containerResult) {
    return new FakeShell().on("incus list ^acme$", containerResult);
  }

  private record CommandResult(int exitCode, String stdout, String stderr) {}

  private static final class PrintWriterBridge extends java.io.PrintWriter {
    private PrintWriterBridge(ByteArrayOutputStream stream) {
      super(stream, true);
    }
  }

  private static final class FakeShell implements ShellExec {
    private final Map<String, Result> scripts = new LinkedHashMap<>();
    private final List<String> invocations = new java.util.ArrayList<>();
    private boolean dryRun;

    private FakeShell on(String pattern, String stdout) {
      return on(pattern, new Result(0, stdout, ""));
    }

    private FakeShell on(String pattern, Result result) {
      scripts.put(pattern, result);
      return this;
    }

    private FakeShell withDryRun(boolean dryRun) {
      this.dryRun = dryRun;
      return this;
    }

    private boolean dryRun() {
      return dryRun;
    }

    private boolean invoked(String pattern) {
      return invocations.stream().anyMatch(command -> command.contains(pattern));
    }

    @Override
    public Result exec(List<String> command) {
      var joined = String.join(" ", command);
      invocations.add(joined);
      for (var entry : scripts.entrySet()) {
        if (joined.contains(entry.getKey())) {
          return entry.getValue();
        }
      }
      return new Result(1, "", "no script for " + joined);
    }

    @Override
    public Result exec(List<String> command, Path workDir, Duration timeout) {
      return exec(command);
    }

    @Override
    public boolean isDryRun() {
      return dryRun;
    }
  }
}
