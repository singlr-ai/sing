/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.api;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SailApiServer implements AutoCloseable {

  private final HttpServer server;
  private final ExecutorService executor;

  public SailApiServer(String host, int port, ApiOperations operations, String token)
      throws IOException {
    server = HttpServer.create(new InetSocketAddress(host, port), 32);
    executor = Executors.newVirtualThreadPerTaskExecutor();
    server.createContext("/", new ApiRouter(operations, new BearerAuth(token)));
    server.setExecutor(executor);
  }

  public void start() {
    server.start();
  }

  public int port() {
    return server.getAddress().getPort();
  }

  @Override
  public void close() {
    server.stop(0);
    executor.close();
  }
}
