/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.engine;

import ai.singlr.sail.config.YamlUtil;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches raw file content from a private GitHub repository using the GitHub raw content API. Uses
 * {@link HttpClient} from the JDK — no external dependencies.
 */
public final class GitHubFetcher {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private GitHubFetcher() {}

  /**
   * Fetches a raw file from a GitHub repository.
   *
   * @param repo the repository in {@code owner/name} format (e.g. {@code your-org/projects})
   * @param path the file path within the repository (e.g. {@code acme-health/sail.yaml})
   * @param token a GitHub personal access token with {@code repo} scope
   * @param ref the branch or tag name (e.g. {@code main})
   * @return the file content as a string
   * @throws IOException on network errors or non-200 responses
   */
  public static String fetchRawFile(String repo, String path, String token, String ref)
      throws IOException, InterruptedException {
    var url = buildUrl(repo, path, ref);
    var builder =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/vnd.github.v3.raw")
            .timeout(TIMEOUT)
            .GET();
    if (token != null && !token.isBlank()) {
      builder.header("Authorization", "token " + token);
    }

    var response =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
            .send(builder.build(), HttpResponse.BodyHandlers.ofString());

    var statusCode = response.statusCode();
    if (statusCode == 404) {
      return null;
    }
    if (statusCode == 401 || statusCode == 403) {
      throw new IOException(
          "GitHub authentication failed (HTTP "
              + statusCode
              + "). Check your token has 'repo' scope.");
    }
    if (statusCode != 200) {
      throw new IOException("Failed to fetch " + repo + "/" + path + " (HTTP " + statusCode + ").");
    }

    return response.body();
  }

  /** A file entry from a GitHub directory tree listing. */
  public record TreeEntry(String path, String relativePath) {}

  /**
   * Lists all files under a directory in a GitHub repository using the Contents API. For each
   * {@code type: "file"} entry whose path starts with the given prefix, a {@link TreeEntry} is
   * returned with the relative path (prefix stripped). Subdirectories are recursed automatically.
   *
   * @param repo the repository in {@code owner/name} format
   * @param dirPath the directory path within the repository (e.g. {@code outline/files})
   * @param token a GitHub personal access token with {@code repo} scope
   * @param ref the branch or tag name
   * @return a list of file entries with relative paths, or an empty list if the directory is
   *     missing
   */
  @SuppressWarnings("unchecked")
  public static List<TreeEntry> fetchDirectoryTree(
      String repo, String dirPath, String token, String ref)
      throws IOException, InterruptedException {
    var prefix = dirPath.endsWith("/") ? dirPath : dirPath + "/";
    return fetchDirectoryTreeRecursive(repo, dirPath, token, ref, prefix);
  }

  @SuppressWarnings("unchecked")
  private static List<TreeEntry> fetchDirectoryTreeRecursive(
      String repo, String dirPath, String token, String ref, String rootPrefix)
      throws IOException, InterruptedException {
    NameValidator.requireValidGitHubRepo(repo);
    var encodedPath = encodePathSegments(dirPath);
    var encodedRef = URLEncoder.encode(ref, StandardCharsets.UTF_8);
    var url =
        "https://api.github.com/repos/" + repo + "/contents/" + encodedPath + "?ref=" + encodedRef;

    var builder =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/vnd.github.v3+json")
            .timeout(TIMEOUT)
            .GET();
    if (token != null && !token.isBlank()) {
      builder.header("Authorization", "token " + token);
    }

    var response =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
            .send(builder.build(), HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() == 404) {
      return List.of();
    }
    if (response.statusCode() == 401 || response.statusCode() == 403) {
      throw new IOException(
          "GitHub authentication failed (HTTP "
              + response.statusCode()
              + "). Check your token has 'repo' scope.");
    }
    if (response.statusCode() != 200) {
      throw new IOException(
          "Failed to list " + repo + "/" + dirPath + " (HTTP " + response.statusCode() + ").");
    }

    var items = YamlUtil.parseList(response.body());
    var result = new ArrayList<TreeEntry>();

    for (var item : items) {
      var type = (String) item.get("type");
      var itemPath = (String) item.get("path");
      if ("file".equals(type)) {
        var relativePath =
            itemPath.startsWith(rootPrefix) ? itemPath.substring(rootPrefix.length()) : itemPath;
        result.add(new TreeEntry(itemPath, relativePath));
      } else if ("dir".equals(type)) {
        result.addAll(fetchDirectoryTreeRecursive(repo, itemPath, token, ref, rootPrefix));
      }
    }
    return List.copyOf(result);
  }

  /** Builds the raw content URL for a file in a GitHub repo. Package-private for testing. */
  static String buildUrl(String repo, String path, String ref) {
    NameValidator.requireValidGitHubRepo(repo);
    var encodedPath = encodePathSegments(path);
    var encodedRef = URLEncoder.encode(ref, StandardCharsets.UTF_8);
    return "https://raw.githubusercontent.com/" + repo + "/" + encodedRef + "/" + encodedPath;
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
