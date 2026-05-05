/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.commands;

import ai.singlr.sail.api.ApiTokenStore;
import ai.singlr.sail.api.SailApiOperations;
import ai.singlr.sail.api.SailApiServer;
import java.net.InetAddress;
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
    description = "Run the authenticated local sail API server.",
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

  @Option(names = "--allow-remote", description = "Allow binding the API to a non-loopback host.")
  private boolean allowRemote;

  @Spec private CommandSpec spec;

  @Override
  public void run() {
    CliCommand.run(spec, this::execute);
  }

  private void execute() throws Exception {
    requireSafeBindAddress(host, allowRemote);
    var resolvedToken = token != null ? token : tokenStore().readOrCreate();
    try (var server = new SailApiServer(host, port, new SailApiOperations(), resolvedToken)) {
      server.start();
      System.out.println(
          Ansi.AUTO.string(
              "  @|green ✓|@ sail API listening on http://" + host + ":" + server.port()));
      new CountDownLatch(1).await();
    }
  }

  static void requireSafeBindAddress(String host, boolean allowRemote) throws Exception {
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("API host must not be blank.");
    }
    var address = InetAddress.getByName(host);
    if (!allowRemote && !address.isLoopbackAddress()) {
      throw new IllegalArgumentException(
          "Refusing to bind sail API to non-loopback host '"
              + host
              + "'. Use --allow-remote only when network access is intentionally protected.");
    }
  }

  private ApiTokenStore tokenStore() {
    return tokenFile != null
        ? new ApiTokenStore(tokenFile, new SecureRandom())
        : ApiTokenStore.defaultStore();
  }
}
