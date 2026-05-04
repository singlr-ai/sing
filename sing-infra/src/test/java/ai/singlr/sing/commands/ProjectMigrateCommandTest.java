package ai.singlr.sing.commands;

import static org.junit.jupiter.api.Assertions.*;

import ai.singlr.sing.Sing;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class ProjectMigrateCommandTest {

  @Test
  void projectHelpListsMigrate() {
    var command = new CommandLine(new Sing());
    var out = new StringWriter();
    command.setOut(new PrintWriter(out));

    var exitCode = command.execute("project", "--help");

    assertEquals(0, exitCode);
    assertTrue(out.toString().contains("migrate"));
  }

  @Test
  void migrateHelpShowsFleetOptions() {
    var command = new CommandLine(new Sing());
    var out = new StringWriter();
    command.setOut(new PrintWriter(out));

    var exitCode = command.execute("project", "migrate", "--help");

    assertEquals(0, exitCode);
    var help = out.toString();
    assertTrue(help.contains("--all"));
    assertTrue(help.contains("--pull-specs"));
    assertTrue(help.contains("--keep-index"));
    assertTrue(help.contains("--json"));
  }

  @Test
  void migrateRequiresSingleProjectOrAll() {
    var command = new CommandLine(new Sing());

    assertNotEquals(0, command.execute("project", "migrate"));
    assertNotEquals(0, command.execute("project", "migrate", "acme", "--all"));
  }
}
