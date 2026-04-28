/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import ai.singlr.sing.config.ClientConfig;
import ai.singlr.sing.config.YamlUtil;
import ai.singlr.sing.engine.Banner;
import ai.singlr.sing.engine.SingPaths;
import java.nio.file.Files;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * Initializes the client config on the engineer's Mac. Creates {@code ~/.sing/config.yaml} with the
 * host connection details. The host can be an IP, hostname, or SSH config alias.
 */
@Command(
    name = "init",
    description = "Initialize sing client — connect to a remote host.",
    mixinStandardHelpOptions = true)
public final class ClientInitCommand implements Runnable {

  @Parameters(index = "0", description = "Host server — IP address, hostname, or SSH config alias.")
  private String host;

  @Spec private CommandSpec spec;

  @Override
  public void run() {
    CliCommand.run(spec, this::execute);
  }

  private void execute() throws Exception {
    var singDir = SingPaths.singDir();
    Files.createDirectories(singDir);

    var configPath = SingPaths.clientConfigPath();
    var config = new ClientConfig(host);
    YamlUtil.dumpToFile(config.toMap(), configPath);

    Banner.printBranding(System.out, Ansi.AUTO);
    System.out.println();
    System.out.println(
        Ansi.AUTO.string("  @|bold,green \u2713|@ Client configured: " + configPath));
    System.out.println(Ansi.AUTO.string("  @|faint Host:|@ " + host));
    System.out.println();
    System.out.println(Ansi.AUTO.string("  Test with: @|bold sing spec list <project>|@"));
  }
}
