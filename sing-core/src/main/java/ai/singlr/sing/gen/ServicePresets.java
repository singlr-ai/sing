/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.gen;

import ai.singlr.sing.config.SingYaml;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Curated catalog of common infrastructure services for interactive project initialization. Each
 * preset maps to a ready-to-use {@link SingYaml.Service} definition with sensible defaults.
 */
public final class ServicePresets {

  private ServicePresets() {}

  public record Preset(String key, String displayName, SingYaml.Service service) {

    /** Extracts the default version tag from the image (e.g. "postgres:16" → "16"). */
    public String defaultVersion() {
      var img = service.image();
      var colon = img.lastIndexOf(':');
      return colon >= 0 ? img.substring(colon + 1) : "latest";
    }

    /** Returns a new Service with the image tag replaced by the given version. */
    public SingYaml.Service withVersion(String version) {
      var img = service.image();
      var colon = img.lastIndexOf(':');
      var base = colon >= 0 ? img.substring(0, colon) : img;
      return new SingYaml.Service(
          base + ":" + version,
          service.ports(),
          service.environment(),
          service.command(),
          service.volumes());
    }
  }

  private static final List<Preset> ALL =
      List.of(
          new Preset(
              "postgres",
              "Postgres 16",
              new SingYaml.Service(
                  "postgres:16",
                  List.of(5432),
                  orderedEnv(
                      "POSTGRES_DB", "app", "POSTGRES_USER", "dev", "POSTGRES_PASSWORD", "dev"),
                  null,
                  List.of("pgdata:/var/lib/postgresql/data"))),
          new Preset(
              "redis", "Redis 7", new SingYaml.Service("redis:7", List.of(6379), null, null, null)),
          new Preset(
              "meilisearch",
              "Meilisearch",
              new SingYaml.Service(
                  "getmeili/meilisearch:latest",
                  List.of(7700),
                  orderedEnv(
                      "MEILI_ENV",
                      "development",
                      "MEILI_NO_ANALYTICS",
                      "true",
                      "MEILI_MASTER_KEY",
                      ""),
                  null,
                  List.of("msdata:/meili_data"))),
          new Preset(
              "redpanda",
              "Redpanda",
              new SingYaml.Service(
                  "redpandadata/redpanda:latest",
                  List.of(9092, 8081, 8082),
                  null,
                  "redpanda start --smp 1 --memory 512M --overprovisioned --kafka-addr"
                      + " 0.0.0.0:9092",
                  null)));

  /** Returns all presets in display order. */
  public static List<Preset> all() {
    return ALL;
  }

  /** Finds a preset by key, or null if not found. */
  public static Preset findByKey(String key) {
    for (var preset : ALL) {
      if (preset.key().equals(key)) {
        return preset;
      }
    }
    return null;
  }

  /** Builds a services map from the selected preset keys, preserving catalog order. */
  public static Map<String, SingYaml.Service> buildServicesMap(List<String> selectedKeys) {
    var result = new LinkedHashMap<String, SingYaml.Service>();
    for (var preset : ALL) {
      if (selectedKeys.contains(preset.key())) {
        result.put(preset.key(), preset.service());
      }
    }
    return result;
  }

  /** Builds an insertion-ordered environment map from key-value pairs. */
  private static Map<String, String> orderedEnv(String... kvPairs) {
    var map = new LinkedHashMap<String, String>();
    for (var i = 0; i < kvPairs.length; i += 2) {
      map.put(kvPairs[i], kvPairs[i + 1]);
    }
    return map;
  }
}
