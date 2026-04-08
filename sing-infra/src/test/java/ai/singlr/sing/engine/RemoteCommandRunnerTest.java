/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import static org.junit.jupiter.api.Assertions.*;

import ai.singlr.sing.config.ClientConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class RemoteCommandRunnerTest {

  private static final ClientConfig CONFIG = new ClientConfig("kubera-server");

  @Test
  void buildSshCommandForNonInteractive() {
    var runner = new RemoteCommandRunner(CONFIG);

    var cmd = runner.buildSshCommand(new String[] {"spec", "list", "kubera"}, false);

    assertEquals("ssh", cmd.getFirst());
    assertFalse(cmd.contains("-t"));
    assertTrue(cmd.contains("kubera-server"));
    assertTrue(cmd.contains("sing"));
    assertTrue(cmd.contains("spec"));
    assertTrue(cmd.contains("list"));
    assertTrue(cmd.contains("kubera"));
  }

  @Test
  void buildSshCommandForInteractive() {
    var runner = new RemoteCommandRunner(CONFIG);

    var cmd = runner.buildSshCommand(new String[] {"shell", "kubera"}, true);

    assertTrue(cmd.contains("-t"));
    assertTrue(cmd.contains("shell"));
  }

  @Test
  void buildSshCommandUsesHostDirectly() {
    var config = new ClientConfig("10.0.0.1");
    var runner = new RemoteCommandRunner(config);

    var cmd = runner.buildSshCommand(new String[] {"up", "demo"}, false);

    assertTrue(cmd.contains("10.0.0.1"));
  }

  @Test
  void buildSshCommandPreservesAllArgs() {
    var runner = new RemoteCommandRunner(CONFIG);
    var args = new String[] {"dispatch", "kubera", "--spec", "auth", "--json"};

    var cmd = runner.buildSshCommand(args, false);

    var singIdx = cmd.indexOf("sing");
    var tail = cmd.subList(singIdx + 1, cmd.size());
    assertEquals(List.of("dispatch", "kubera", "--spec", "auth", "--json"), tail);
  }

  @Test
  void buildSshCommandForEmptyArgs() {
    var runner = new RemoteCommandRunner(CONFIG);

    var cmd = runner.buildSshCommand(new String[] {}, false);

    assertTrue(cmd.contains("sing"));
    assertEquals(cmd.indexOf("sing"), cmd.size() - 1);
  }

  @Test
  void isLocalCommandRecognizesVersionUpgradeAndInit() {
    assertTrue(RemoteCommandRunner.isLocalCommand("--version"));
    assertTrue(RemoteCommandRunner.isLocalCommand("-V"));
    assertTrue(RemoteCommandRunner.isLocalCommand("upgrade"));
    assertTrue(RemoteCommandRunner.isLocalCommand("init"));
    assertFalse(RemoteCommandRunner.isLocalCommand("spec"));
    assertFalse(RemoteCommandRunner.isLocalCommand("dispatch"));
  }

  @Test
  void isHostOnlyCommandRecognizesHostSubcommands() {
    assertTrue(RemoteCommandRunner.isHostOnlyCommand("host"));
    assertFalse(RemoteCommandRunner.isHostOnlyCommand("project"));
  }

  @Test
  void isInteractiveCommandRecognizesShellAndExec() {
    assertTrue(RemoteCommandRunner.isInteractiveCommand("shell"));
    assertTrue(RemoteCommandRunner.isInteractiveCommand("exec"));
    assertFalse(RemoteCommandRunner.isInteractiveCommand("dispatch"));
  }

  @Test
  void sshCommandIsImmutable() {
    var runner = new RemoteCommandRunner(CONFIG);

    var cmd = runner.buildSshCommand(new String[] {"up", "demo"}, false);

    assertThrows(UnsupportedOperationException.class, () -> cmd.add("extra"));
  }

  @Test
  void sshCommandIsSimpleWithAlias() {
    var runner = new RemoteCommandRunner(CONFIG);

    var cmd = runner.buildSshCommand(new String[] {"ps", "kubera"}, false);

    assertEquals(List.of("ssh", "kubera-server", "sing", "ps", "kubera"), cmd);
  }
}
