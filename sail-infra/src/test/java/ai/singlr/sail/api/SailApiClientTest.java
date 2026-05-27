/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.api;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SailApiClientTest {

  private SailApiServer server;
  private SailApiClient client;

  @BeforeEach
  void setUp() throws Exception {
    server = new SailApiServer("127.0.0.1", 0, new TestOperations(), "test-token");
    server.start();
    client = new SailApiClient("http://127.0.0.1:" + server.port(), "test-token");
  }

  @AfterEach
  void tearDown() {
    if (client != null) client.close();
    if (server != null) server.close();
  }

  @Test
  void getHealth() throws IOException {
    var result = client.get("/v1/health");
    assertEquals("ok", result.get("status"));
  }

  @Test
  void getSpecBoard() throws IOException {
    var result = client.get("/v1/specs/board");
    assertNotNull(result.get("pending"));
  }

  @Test
  void postCreateSpec() throws IOException {
    var body = new LinkedHashMap<String, Object>();
    body.put("id", "test-spec");
    body.put("title", "Test Spec");
    body.put("status", "draft");
    var result = client.post("/v1/specs", body);
    assertNotNull(result.get("spec"));
  }

  @Test
  void putUpdateSpec() throws IOException {
    var body = new LinkedHashMap<String, Object>();
    body.put("title", "Updated Title");
    var result = client.put("/v1/specs/test-spec", body);
    assertNotNull(result.get("spec"));
  }

  @Test
  void deleteSpec() throws IOException {
    var result = client.delete("/v1/specs/test-spec");
    assertEquals(true, result.get("deleted"));
  }

  @Test
  void invalidTokenThrowsIOException() {
    try (var badClient = new SailApiClient("http://127.0.0.1:" + server.port(), "wrong-token")) {
      assertThrows(IOException.class, () -> badClient.get("/v1/specs/board"));
    }
  }

  @Test
  void trailingSlashInBaseUrlIsHandled() throws IOException {
    try (var slashClient =
        new SailApiClient("http://127.0.0.1:" + server.port() + "/", "test-token")) {
      var result = slashClient.get("/v1/health");
      assertEquals("ok", result.get("status"));
    }
  }

  @Test
  void putSpecContent() throws IOException {
    var body = new LinkedHashMap<String, Object>();
    body.put("body", "# Spec content");
    body.put("plan", "## Plan");
    var result = client.put("/v1/specs/test-spec/content", body);
    assertEquals("# Spec content", result.get("body"));
  }
}
