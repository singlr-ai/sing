/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.commands;

import ai.singlr.sail.config.ClientConfig;
import ai.singlr.sail.config.YamlUtil;
import ai.singlr.sail.engine.Banner;
import ai.singlr.sail.engine.SailPaths;
import java.nio.file.Files;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * Initializes the client config on the engineer's Mac. Creates {@code ~/.sail/config.yaml} with the
 * host connection details. The host can be an IP, hostname, or SSH config alias.
 */
@Command(
    name = "init",
    description = "Initialize sail client — connect to a remote host.",
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
    var sailDir = SailPaths.sailDir();
    Files.createDirectories(sailDir);

    var configPath = SailPaths.clientConfigPath();
    var config = new ClientConfig(host);
    YamlUtil.dumpToFile(config.toMap(), configPath);

    Banner.printBranding(System.out, Ansi.AUTO);
    System.out.println();
    System.out.println(
        Ansi.AUTO.string("  @|bold,green \u2713|@ Client configured: " + configPath));
    System.out.println(Ansi.AUTO.string("  @|faint Host:|@ " + host));
    System.out.println();
    System.out.println(Ansi.AUTO.string("  Test with: @|bold sail spec list <project>|@"));
  }
}
