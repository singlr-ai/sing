/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import ai.singlr.sing.api.ApiTokenStore;
import ai.singlr.sing.api.SingApiOperations;
import ai.singlr.sing.api.SingApiServer;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.concurrent.CountDownLatch;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
    name = "api",
    description = "Run the authenticated local sing API server.",
    mixinStandardHelpOptions = true)
public final class ApiCommand implements Runnable {

  @Option(names = "--host", description = "Host to bind.", defaultValue = "127.0.0.1")
  private String host;

  @Option(names = "--port", description = "Port to bind.", defaultValue = "7070")
  private int port;

  @Option(names = "--token", description = "Bearer token. Defaults to token-file contents.")
  private String token;

  @Option(names = "--token-file", description = "Bearer token file.")
  private Path tokenFile;

  @Spec private CommandSpec spec;

  @Override
  public void run() {
    CliCommand.run(spec, this::execute);
  }

  private void execute() throws Exception {
    var resolvedToken = token != null ? token : tokenStore().readOrCreate();
    try (var server = new SingApiServer(host, port, new SingApiOperations(), resolvedToken)) {
      server.start();
      System.out.println(
          Ansi.AUTO.string(
              "  @|green ✓|@ sing API listening on http://" + host + ":" + server.port()));
      new CountDownLatch(1).await();
    }
  }

  private ApiTokenStore tokenStore() {
    return tokenFile != null
        ? new ApiTokenStore(tokenFile, new SecureRandom())
        : ApiTokenStore.defaultStore();
  }
}
