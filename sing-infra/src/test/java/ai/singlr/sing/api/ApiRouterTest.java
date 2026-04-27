/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.api;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiRouterTest {

  @Test
  void healthDoesNotRequireAuth() throws Exception {
    try (var server = server()) {
      var response = get(server, "/v1/health", null);

      assertEquals(200, response.statusCode());
      assertTrue(response.body().contains("\"status\": \"ok\""));
      assertTrue(response.body().contains("\"schema_version\": 1"));
    }
  }

  @Test
  void protectedRoutesRequireBearerToken() throws Exception {
    try (var server = server()) {
      var response = get(server, "/v1/projects/acme/agent", null);

      assertEquals(401, response.statusCode());
      assertTrue(response.body().contains("missing_bearer_token"));
    }
  }

  @Test
  void protectedRoutesRejectWrongBearerToken() throws Exception {
    try (var server = server()) {
      var response = get(server, "/v1/projects/acme/agent", "wrong");

      assertEquals(403, response.statusCode());
      assertTrue(response.body().contains("invalid_bearer_token"));
    }
  }

  @Test
  void dispatchParsesJsonBody() throws Exception {
    try (var server = server()) {
      var response = post(server, "/v1/projects/acme/dispatch", "token", "{\"spec_id\": \"auth\"}");

      assertEquals(200, response.statusCode());
      assertTrue(response.body().contains("\"spec_id\": \"auth\""));
      assertTrue(response.body().contains("\"project\": \"acme\""));
    }
  }

  @Test
  void methodMismatchReturnsMethodNotAllowed() throws Exception {
    try (var server = server()) {
      var response = get(server, "/v1/projects/acme/dispatch", "token");

      assertEquals(405, response.statusCode());
      assertTrue(response.body().contains("method_not_allowed"));
    }
  }

  @Test
  void unknownRouteReturnsNotFound() throws Exception {
    try (var server = server()) {
      var response = get(server, "/v1/projects/acme/unknown", "token");

      assertEquals(404, response.statusCode());
      assertTrue(response.body().contains("not_found"));
    }
  }

  @Test
  void invalidProjectNameReturnsUnprocessableEntity() throws Exception {
    try (var server = server()) {
      var response = get(server, "/v1/projects/Bad_Name/agent", "token");

      assertEquals(422, response.statusCode());
      assertTrue(response.body().contains("invalid_request"));
    }
  }

  @Test
  void invalidSpecIdReturnsUnprocessableEntity() throws Exception {
    try (var server = server()) {
      var response = get(server, "/v1/projects/acme/specs/Bad_Name", "token");

      assertEquals(422, response.statusCode());
      assertTrue(response.body().contains("invalid_request"));
    }
  }

  @Test
  void invalidJsonReturnsBadRequest() throws Exception {
    try (var server = server()) {
      var response = post(server, "/v1/projects/acme/dispatch", "token", "{");

      assertEquals(400, response.statusCode());
      assertTrue(response.body().contains("invalid_json"));
    }
  }

  @Test
  void unsupportedContentTypeReturnsUnsupportedMediaType() throws Exception {
    try (var server = server()) {
      var request =
          HttpRequest.newBuilder(uri(server, "/v1/projects/acme/dispatch"))
              .header("Authorization", "Bearer token")
              .header("Content-Type", "text/plain")
              .POST(HttpRequest.BodyPublishers.ofString("{}"))
              .build();

      var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

      assertEquals(415, response.statusCode());
      assertTrue(response.body().contains("unsupported_media_type"));
    }
  }

  @Test
  void oversizedBodyReturnsPayloadTooLarge() throws Exception {
    try (var server = server()) {
      var body = "{\"value\":\"" + "x".repeat(70_000) + "\"}";
      var response = post(server, "/v1/projects/acme/dispatch", "token", body);

      assertEquals(413, response.statusCode());
      assertTrue(response.body().contains("request_too_large"));
    }
  }

  @Test
  void invalidTailReturnsUnprocessableEntity() throws Exception {
    try (var server = server()) {
      var response = get(server, "/v1/projects/acme/agent/log?tail=0", "token");

      assertEquals(422, response.statusCode());
      assertTrue(response.body().contains("invalid_tail"));
    }
  }

  @Test
  void routesAgentAndSpecEndpoints() throws Exception {
    try (var server = server()) {
      assertEquals(200, get(server, "/v1/projects/acme", "token").statusCode());
      assertEquals(200, get(server, "/v1/projects/acme/specs", "token").statusCode());
      assertEquals(200, get(server, "/v1/projects/acme/specs/auth", "token").statusCode());
      assertEquals(200, get(server, "/v1/projects/acme/agent", "token").statusCode());
      assertEquals(200, get(server, "/v1/projects/acme/agent/log", "token").statusCode());
      assertEquals(200, post(server, "/v1/projects/acme/agent/stop", "token", "{}").statusCode());
      assertEquals(200, post(server, "/v1/projects/acme/agent/report", "token", "{}").statusCode());
    }
  }

  @Test
  void operationExceptionsBecomeStructuredErrors() throws Exception {
    try (var server = new SingApiServer("127.0.0.1", 0, new FailingOperations(), "token")) {
      server.start();

      var response = get(server, "/v1/projects/acme/agent", "token");

      assertEquals(409, response.statusCode());
      assertTrue(response.body().contains("agent_busy"));
    }
  }

  private static SingApiServer server() throws IOException {
    var server = new SingApiServer("127.0.0.1", 0, new FakeOperations(), "token");
    server.start();
    return server;
  }

  private static HttpResponse<String> get(SingApiServer server, String path, String token)
      throws Exception {
    var builder = HttpRequest.newBuilder(uri(server, path)).GET();
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static HttpResponse<String> post(
      SingApiServer server, String path, String token, String body) throws Exception {
    var builder =
        HttpRequest.newBuilder(uri(server, path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static URI uri(SingApiServer server, String path) {
    return URI.create("http://127.0.0.1:" + server.port() + path);
  }

  private static class FakeOperations implements ApiOperations {
    @Override
    public Map<String, Object> health() {
      return Map.of("status", "ok");
    }

    @Override
    public Map<String, Object> project(String project) {
      return Map.of("project", project, "container_status", "running");
    }

    @Override
    public Map<String, Object> specs(String project) {
      return Map.of("project", project, "specs", java.util.List.of());
    }

    @Override
    public Map<String, Object> spec(String project, String specId) {
      return Map.of("project", project, "spec_id", specId);
    }

    @Override
    public Map<String, Object> dispatch(String project, Map<String, Object> request) {
      var map = new LinkedHashMap<String, Object>();
      map.put("project", project);
      map.put("spec_id", request.get("spec_id"));
      return map;
    }

    @Override
    public Map<String, Object> agentStatus(String project) {
      return Map.of("project", project, "agent_running", false);
    }

    @Override
    public Map<String, Object> agentLog(String project, int tail) {
      return Map.of("project", project, "tail", tail);
    }

    @Override
    public Map<String, Object> stopAgent(String project) {
      return Map.of("project", project, "stopped", false);
    }

    @Override
    public Map<String, Object> agentReport(String project) {
      return Map.of("project", project, "session_status", "No session");
    }
  }

  private static final class FailingOperations extends FakeOperations {
    @Override
    public Map<String, Object> agentStatus(String project) {
      throw new ApiException(409, "agent_busy", "Agent is busy.", "Wait.");
    }
  }
}
