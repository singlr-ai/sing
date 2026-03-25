/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.config;

import ai.singlr.sing.gen.SingYamlGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Updates an existing {@code sing.yaml} by adding services or repos. Reads the current file, merges
 * the new entry, and writes back via {@link SingYamlGenerator} to preserve the standard commented
 * format.
 *
 * <p>Note: user-added comments are not preserved across updates — the generator produces its own
 * standard inline comments.
 */
public final class SingYamlUpdater {

  private SingYamlUpdater() {}

  /**
   * Adds a service to the sing.yaml at the given path. If the services section doesn't exist, it is
   * created. If the service name already exists, throws.
   */
  public static SingYaml addService(Path singYamlPath, String serviceName, SingYaml.Service service)
      throws IOException {
    var config = readConfig(singYamlPath);
    var services =
        config.services() != null
            ? new LinkedHashMap<>(config.services())
            : new LinkedHashMap<String, SingYaml.Service>();
    if (services.containsKey(serviceName)) {
      throw new IllegalStateException(
          "Service '" + serviceName + "' already exists in " + singYamlPath);
    }
    services.put(serviceName, service);
    var updated = withServices(config, Map.copyOf(services));
    writeConfig(singYamlPath, updated);
    return updated;
  }

  /**
   * Removes a service from the sing.yaml at the given path. Throws if the service doesn't exist.
   */
  public static SingYaml removeService(Path singYamlPath, String serviceName) throws IOException {
    var config = readConfig(singYamlPath);
    var services =
        config.services() != null
            ? new LinkedHashMap<>(config.services())
            : new LinkedHashMap<String, SingYaml.Service>();
    if (!services.containsKey(serviceName)) {
      throw new IllegalStateException("Service '" + serviceName + "' not found in " + singYamlPath);
    }
    services.remove(serviceName);
    var updated = withServices(config, services.isEmpty() ? null : Map.copyOf(services));
    writeConfig(singYamlPath, updated);
    return updated;
  }

  /**
   * Adds a repo to the sing.yaml at the given path. If the repos section doesn't exist, it is
   * created. If a repo with the same path already exists, throws.
   */
  public static SingYaml addRepo(Path singYamlPath, SingYaml.Repo repo) throws IOException {
    var config = readConfig(singYamlPath);
    var repos =
        config.repos() != null ? new ArrayList<>(config.repos()) : new ArrayList<SingYaml.Repo>();
    for (var existing : repos) {
      if (existing.path().equals(repo.path())) {
        throw new IllegalStateException(
            "Repo with path '" + repo.path() + "' already exists in " + singYamlPath);
      }
    }
    repos.add(repo);
    var updated = withRepos(config, List.copyOf(repos));
    writeConfig(singYamlPath, updated);
    return updated;
  }

  private static SingYaml readConfig(Path path) throws IOException {
    if (!Files.exists(path)) {
      throw new IllegalStateException("sing.yaml not found: " + path.toAbsolutePath());
    }
    return SingYaml.fromMap(YamlUtil.parseFile(path));
  }

  private static void writeConfig(Path path, SingYaml config) throws IOException {
    var yaml = SingYamlGenerator.generate(config);
    Files.writeString(path, yaml);
  }

  private static SingYaml withServices(SingYaml c, Map<String, SingYaml.Service> services) {
    return new SingYaml(
        c.name(),
        c.description(),
        c.resources(),
        c.image(),
        c.packages(),
        c.runtimes(),
        c.git(),
        c.repos(),
        services,
        c.processes(),
        c.agent(),
        c.agentContext(),
        c.ssh());
  }

  private static SingYaml withRepos(SingYaml c, List<SingYaml.Repo> repos) {
    return new SingYaml(
        c.name(),
        c.description(),
        c.resources(),
        c.image(),
        c.packages(),
        c.runtimes(),
        c.git(),
        repos,
        c.services(),
        c.processes(),
        c.agent(),
        c.agentContext(),
        c.ssh());
  }
}
