/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.config;

import ai.singlr.sail.engine.NameValidator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A single spec representing one unit of work. Each spec lives in its own directory inside the
 * {@code specs/} folder, containing a {@code spec.yaml} (this record) and optionally a {@code
 * spec.md} with the detailed description.
 *
 * @param id directory name and unique identifier
 * @param title short human-readable title
 * @param status lifecycle state: pending, in_progress, review, done
 * @param assignee engineer responsible (nullable, matches git identity)
 * @param dependsOn IDs of specs that must be done first
 * @param repos repository paths this spec should branch and work in
 * @param branch git branch for this spec's work (nullable)
 */
public record Spec(
    String id,
    String title,
    String status,
    String assignee,
    List<String> dependsOn,
    List<String> repos,
    String branch) {

  public Spec(
      String id,
      String title,
      String status,
      String assignee,
      List<String> dependsOn,
      String branch) {
    this(id, title, status, assignee, dependsOn, List.of(), branch);
  }

  @SuppressWarnings("unchecked")
  public static Spec fromMap(Map<String, Object> map) {
    var id = (String) map.get("id");
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("spec.id is required");
    }
    NameValidator.requireValidSpecId(id);
    var title = Objects.requireNonNullElse((String) map.get("title"), "");
    var status = Objects.requireNonNullElse((String) map.get("status"), "pending");
    var assignee = (String) map.get("assignee");
    var dependsOn = (List<String>) map.get("depends_on");
    var repos = reposFromMap(map);
    var branch = (String) map.get("branch");
    return new Spec(
        id,
        title,
        status,
        assignee,
        dependsOn != null ? List.copyOf(dependsOn) : List.of(),
        repos,
        branch);
  }

  public Map<String, Object> toMap() {
    var map = new LinkedHashMap<String, Object>();
    map.put("id", id);
    if (!title.isBlank()) {
      map.put("title", title);
    }
    map.put("status", status);
    if (assignee != null) {
      map.put("assignee", assignee);
    }
    if (!dependsOn.isEmpty()) {
      map.put("depends_on", dependsOn);
    }
    if (repos.size() == 1) {
      map.put("repo", repos.getFirst());
    } else if (!repos.isEmpty()) {
      map.put("repos", repos);
    }
    if (branch != null) {
      map.put("branch", branch);
    }
    return map;
  }

  private static List<String> reposFromMap(Map<String, Object> map) {
    var repo = (String) map.get("repo");
    var repos = (List<String>) map.get("repos");
    if (repo != null && repos != null) {
      throw new IllegalArgumentException("spec may define repo or repos, not both");
    }
    if (repo != null) {
      return validatedRepos(List.of(repo));
    }
    if (repos != null) {
      return validatedRepos(repos);
    }
    return List.of();
  }

  private static List<String> validatedRepos(List<String> repos) {
    repos.forEach(repo -> NameValidator.requireSafePath(repo, "spec.repo"));
    return List.copyOf(repos);
  }
}
