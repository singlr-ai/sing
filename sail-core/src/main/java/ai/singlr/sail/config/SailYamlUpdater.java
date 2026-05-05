/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.config;

import ai.singlr.sail.gen.SailYamlGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Updates an existing {@code sail.yaml} by adding services or repos. Reads the current file, merges
 * the new entry, and writes back via {@link SailYamlGenerator} to preserve the standard commented
 * format.
 *
 * <p>Note: user-added comments are not preserved across updates — the generator produces its own
 * standard inline comments.
 */
public final class SailYamlUpdater {

  private SailYamlUpdater() {}

  /**
   * Adds a service to the sail.yaml at the given path. If the services section doesn't exist, it is
   * created. If the service name already exists, throws.
   */
  public static SailYaml addService(Path singYamlPath, String serviceName, SailYaml.Service service)
      throws IOException {
    var config = readConfig(singYamlPath);
    var services =
        config.services() != null
            ? new LinkedHashMap<>(config.services())
            : new LinkedHashMap<String, SailYaml.Service>();
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
   * Removes a service from the sail.yaml at the given path. Throws if the service doesn't exist.
   */
  public static SailYaml removeService(Path singYamlPath, String serviceName) throws IOException {
    var config = readConfig(singYamlPath);
    var services =
        config.services() != null
            ? new LinkedHashMap<>(config.services())
            : new LinkedHashMap<String, SailYaml.Service>();
    if (!services.containsKey(serviceName)) {
      throw new IllegalStateException("Service '" + serviceName + "' not found in " + singYamlPath);
    }
    services.remove(serviceName);
    var updated = withServices(config, services.isEmpty() ? null : Map.copyOf(services));
    writeConfig(singYamlPath, updated);
    return updated;
  }

  /**
   * Adds a repo to the sail.yaml at the given path. If the repos section doesn't exist, it is
   * created. If a repo with the same path already exists, throws.
   */
  public static SailYaml addRepo(Path singYamlPath, SailYaml.Repo repo) throws IOException {
    var config = readConfig(singYamlPath);
    var repos =
        config.repos() != null ? new ArrayList<>(config.repos()) : new ArrayList<SailYaml.Repo>();
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

  /**
   * Updates the resources block in the sail.yaml at the given path. Any null argument preserves the
   * existing value.
   */
  public static SailYaml updateResources(Path singYamlPath, Integer cpu, String memory, String disk)
      throws IOException {
    var config = readConfig(singYamlPath);
    var updated = withResources(config, mergeResources(config.resources(), cpu, memory, disk));
    writeConfig(singYamlPath, updated);
    return updated;
  }

  /**
   * Returns the merged resources block for a partial resource update. Any null argument preserves
   * the existing value.
   */
  public static SailYaml.Resources mergeResources(
      SailYaml.Resources current, Integer cpu, String memory, String disk) {
    if (current == null) {
      throw new IllegalStateException("sail.yaml must have a resources section");
    }
    if (cpu != null && cpu < 1) {
      throw new IllegalArgumentException("resources.cpu must be >= 1");
    }
    if (memory != null && memory.isBlank()) {
      throw new IllegalArgumentException("resources.memory must not be blank");
    }
    if (disk != null && disk.isBlank()) {
      throw new IllegalArgumentException("resources.disk must not be blank");
    }
    var merged =
        Map.<String, Object>of(
            "cpu", cpu != null ? cpu : current.cpu(),
            "memory", memory != null ? memory : current.memory(),
            "disk", disk != null ? disk : current.disk());
    return SailYaml.Resources.fromMap(merged);
  }

  private static SailYaml readConfig(Path path) throws IOException {
    if (!Files.exists(path)) {
      throw new IllegalStateException("sail.yaml not found: " + path.toAbsolutePath());
    }
    return SailYaml.fromMap(YamlUtil.parseFile(path));
  }

  private static void writeConfig(Path path, SailYaml config) throws IOException {
    var yaml = SailYamlGenerator.generate(config);
    Files.writeString(path, yaml);
  }

  private static SailYaml withServices(SailYaml c, Map<String, SailYaml.Service> services) {
    return new SailYaml(
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

  private static SailYaml withRepos(SailYaml c, List<SailYaml.Repo> repos) {
    return new SailYaml(
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

  private static SailYaml withResources(SailYaml c, SailYaml.Resources resources) {
    return new SailYaml(
        c.name(),
        c.description(),
        resources,
        c.image(),
        c.packages(),
        c.runtimes(),
        c.git(),
        c.repos(),
        c.services(),
        c.processes(),
        c.agent(),
        c.agentContext(),
        c.ssh());
  }
}
