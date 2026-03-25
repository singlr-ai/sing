/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import ai.singlr.sing.config.YamlUtil;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Pushes file content to a private GitHub repository using the GitHub Contents API. Uses {@link
 * HttpClient} from the JDK — no external dependencies. Symmetric counterpart to {@link
 * GitHubFetcher}.
 */
public final class GitHubPusher {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final String API_BASE = "https://api.github.com";

  /** Result of a push operation. */
  public record PushResult(String commitSha, String commitUrl, boolean created) {}

  private GitHubPusher() {}

  /**
   * Creates or updates a file in a GitHub repository via the Contents API.
   *
   * @param repo the repository in {@code owner/name} format
   * @param path the file path within the repository
   * @param content the file content to push
   * @param message the commit message
   * @param token a GitHub personal access token with {@code repo} scope
   * @param ref the target branch name
   * @return the push result with commit SHA and URL
   * @throws IOException on network errors or non-2xx responses
   */
  public static PushResult pushFile(
      String repo, String path, String content, String message, String token, String ref)
      throws IOException, InterruptedException {
    var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    var existingSha = fetchExistingSha(client, repo, path, token, ref);
    var created = existingSha == null;

    var encoded = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
    var body = buildPutBody(message, encoded, existingSha, ref);

    NameValidator.requireValidGitHubRepo(repo);
    var url = API_BASE + "/repos/" + repo + "/contents/" + encodePathSegments(path);
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "token " + token)
            .header("Accept", "application/vnd.github.v3+json")
            .header("Content-Type", "application/json")
            .timeout(TIMEOUT)
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build();

    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
    var statusCode = response.statusCode();

    if (statusCode == 409) {
      throw new IOException(
          "Conflict: the file was modified on the remote since it was last fetched."
              + "\n  Pull the latest version with 'sing project pull' and try again.");
    }
    if (statusCode == 401 || statusCode == 403) {
      throw new IOException(
          "GitHub authentication failed (HTTP "
              + statusCode
              + "). Check your token has 'repo' scope.");
    }
    if (statusCode != 200 && statusCode != 201) {
      throw new IOException("Failed to push " + repo + "/" + path + " (HTTP " + statusCode + ").");
    }

    return parsePushResponse(response.body(), created);
  }

  /**
   * Fetches the SHA of an existing file from the Contents API. Returns null if the file does not
   * exist (404).
   */
  @SuppressWarnings("unchecked")
  static String fetchExistingSha(
      HttpClient client, String repo, String path, String token, String ref)
      throws IOException, InterruptedException {
    var url =
        API_BASE
            + "/repos/"
            + repo
            + "/contents/"
            + encodePathSegments(path)
            + "?ref="
            + URLEncoder.encode(ref, StandardCharsets.UTF_8);
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "token " + token)
            .header("Accept", "application/vnd.github.v3+json")
            .timeout(TIMEOUT)
            .GET()
            .build();

    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() == 404) {
      return null;
    }
    if (response.statusCode() != 200) {
      throw new IOException(
          "Failed to check existing file " + path + " (HTTP " + response.statusCode() + ").");
    }

    var map = (Map<String, Object>) YamlUtil.parseMap(response.body());
    return map.get("sha") instanceof String sha ? sha : null;
  }

  /** Builds the JSON body for the PUT request. Package-private for testing. */
  static String buildPutBody(String message, String base64Content, String sha, String branch) {
    var sb = new StringBuilder();
    sb.append("{");
    sb.append("\"message\":").append(jsonString(message));
    sb.append(",\"content\":").append(jsonString(base64Content));
    if (sha != null) {
      sb.append(",\"sha\":").append(jsonString(sha));
    }
    sb.append(",\"branch\":").append(jsonString(branch));
    sb.append("}");
    return sb.toString();
  }

  @SuppressWarnings("unchecked")
  private static PushResult parsePushResponse(String responseBody, boolean created) {
    var map = (Map<String, Object>) YamlUtil.parseMap(responseBody);
    var commitMap = (Map<String, Object>) map.get("commit");
    var commitSha = commitMap != null && commitMap.get("sha") instanceof String s ? s : "";
    var commitUrl = commitMap != null && commitMap.get("html_url") instanceof String s ? s : "";
    return new PushResult(commitSha, commitUrl, created);
  }

  /** Escapes a string for JSON output. */
  private static String jsonString(String value) {
    return "\""
        + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        + "\"";
  }

  /** URL-encodes each segment of a path while preserving '/' separators. */
  private static String encodePathSegments(String path) {
    var segments = path.split("/", -1);
    var sb = new StringBuilder();
    for (var i = 0; i < segments.length; i++) {
      if (i > 0) sb.append('/');
      sb.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8));
    }
    return sb.toString();
  }
}
