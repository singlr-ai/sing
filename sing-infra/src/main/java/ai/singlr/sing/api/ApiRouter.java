/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.api;

import ai.singlr.sing.config.YamlUtil;
import ai.singlr.sing.engine.NameValidator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

public final class ApiRouter implements HttpHandler {

  private final ApiOperations operations;
  private final BearerAuth auth;

  public ApiRouter(ApiOperations operations, BearerAuth auth) {
    this.operations = operations;
    this.auth = auth;
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    try {
      var response = route(exchange);
      write(exchange, response);
    } catch (ApiException e) {
      write(exchange, ApiResponse.error(e.failure()));
    } catch (IllegalArgumentException e) {
      write(
          exchange,
          ApiResponse.error(
              new Result.Failure<>(ErrorCode.INVALID_REQUEST, e.getMessage(), null, null, e)));
    } catch (Exception e) {
      write(
          exchange,
          ApiResponse.error(
              new Result.Failure<>(
                  ErrorCode.INTERNAL,
                  "sing API request failed.",
                  "Check sing API logs.",
                  null,
                  e)));
    } finally {
      exchange.close();
    }
  }

  ApiResponse route(HttpExchange exchange) throws IOException {
    var method = exchange.getRequestMethod();
    var path = cleanPath(exchange.getRequestURI());
    if (method.equals("GET") && path.equals("/v1/health")) {
      return ApiResponse.from(operations.health());
    }
    auth.require(exchange);
    var segments = path.substring(1).split("/");
    if (segments.length < 3 || !segments[0].equals("v1") || !segments[1].equals("projects")) {
      throw notFound();
    }
    var project = segments[2];
    NameValidator.requireValidProjectName(project);
    if (segments.length == 3 && method.equals("GET")) {
      return ApiResponse.from(operations.project(project));
    }
    if (segments.length == 4 && segments[3].equals("specs") && method.equals("GET")) {
      return ApiResponse.from(operations.specs(project));
    }
    if (segments.length == 5 && segments[3].equals("specs") && method.equals("GET")) {
      NameValidator.requireValidSpecId(segments[4]);
      return ApiResponse.from(operations.spec(project, segments[4]));
    }
    if (segments.length == 4 && segments[3].equals("dispatch") && method.equals("POST")) {
      return ApiResponse.from(operations.dispatch(project, JsonBody.readDispatchRequest(exchange)));
    }
    if (segments.length == 4 && segments[3].equals("agent") && method.equals("GET")) {
      return ApiResponse.from(operations.agentStatus(project));
    }
    if (segments.length == 5
        && segments[3].equals("agent")
        && segments[4].equals("log")
        && method.equals("GET")) {
      return ApiResponse.from(operations.agentLog(project, tail(exchange.getRequestURI())));
    }
    if (segments.length == 5
        && segments[3].equals("agent")
        && segments[4].equals("stop")
        && method.equals("POST")) {
      return ApiResponse.from(operations.stopAgent(project));
    }
    if (segments.length == 5
        && segments[3].equals("agent")
        && segments[4].equals("report")
        && method.equals("POST")) {
      return ApiResponse.from(operations.agentReport(project));
    }
    if (knownPath(segments)) {
      throw new ApiException(
          ErrorCode.METHOD_NOT_ALLOWED, "HTTP method is not allowed for this endpoint.");
    }
    throw notFound();
  }

  private static boolean knownPath(String[] segments) {
    return segments.length >= 4
        && segments[0].equals("v1")
        && segments[1].equals("projects")
        && (segments[3].equals("specs")
            || segments[3].equals("dispatch")
            || segments[3].equals("agent"));
  }

  private static int tail(URI uri) {
    var query = uri.getRawQuery();
    if (query == null || query.isBlank()) {
      return 200;
    }
    for (var part : query.split("&")) {
      var pair = part.split("=", 2);
      if (pair.length == 2 && pair[0].equals("tail")) {
        try {
          var value = Integer.parseInt(pair[1]);
          if (value >= 1 && value <= 5000) {
            return value;
          }
        } catch (NumberFormatException ignored) {
        }
        throw new ApiException(ErrorCode.INVALID_TAIL, "tail must be between 1 and 5000.");
      }
    }
    return 200;
  }

  private static String cleanPath(URI uri) {
    var path = uri.getPath();
    return path == null || path.isBlank() ? "/" : path;
  }

  private static ApiException notFound() {
    return new ApiException(ErrorCode.NOT_FOUND, "API endpoint was not found.");
  }

  private static void write(HttpExchange exchange, ApiResponse response) throws IOException {
    var body =
        YamlUtil.dumpJson(new LinkedHashMap<>(response.body())).getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(response.status(), body.length);
    try (var output = exchange.getResponseBody()) {
      output.write(body);
    }
  }
}
