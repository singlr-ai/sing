/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.sync;

import ai.singlr.sail.config.YamlUtil;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The line protocol for one sync session over a Door-2 SSH channel: the node and main exchange one
 * JSON object per line, never spanning lines because {@link YamlUtil#dumpJson} escapes newlines in
 * spec bodies. Both sides share this single definition of the wire so the encode and decode never
 * drift. The node issues {@link Fetch} once (main answers with its whole shared state), a {@link
 * Commit} per pushed entity (main mints and returns the authoritative rev), and {@link Bye} to end
 * the session; main answers each with the matching {@link Response}.
 *
 * <p>A {@code null} snapshot is a deletion — it crosses the wire as an explicit JSON {@code null},
 * distinct from an absent key, so a tombstone is never mistaken for a missing row.
 */
public final class SyncWire {

  private static final String OP = "op";
  private static final String ID = "id";
  private static final String MAX_SEQ = "maxSeq";
  private static final String ENTITIES = "entities";
  private static final String REV = "rev";
  private static final String SNAPSHOT = "snapshot";
  private static final String ERROR = "error";

  private static final String OP_FETCH = "fetch";
  private static final String OP_COMMIT = "commit";
  private static final String OP_BYE = "bye";

  private SyncWire() {}

  public sealed interface Request permits Fetch, Commit, Bye {}

  /** Ask main for its whole shared state — the merge view the engine reconciles against. */
  public record Fetch() implements Request {}

  /** Push one entity to main; a {@code null} snapshot pushes a deletion. */
  public record Commit(String entityId, Map<String, Object> snapshot) implements Request {}

  /** End the session; main returns nothing. */
  public record Bye() implements Request {}

  public sealed interface Response permits Fetched, Committed, Failed {}

  /** Main's authoritative rev and snapshot for one entity; either may be {@code null}. */
  public record Snapshot(String rev, Map<String, Object> snapshot) {}

  /** Main's answer to {@link Fetch}: its identity, high-water sequence, and every shared entity. */
  public record Fetched(String mainId, long maxSeq, Map<String, Snapshot> entities)
      implements Response {}

  /** Main accepted a {@link Commit}: the minted rev and main's new high-water sequence. */
  public record Committed(String rev, long maxSeq) implements Response {}

  /** Main refused a request — e.g. a read-only FDE attempting to push. */
  public record Failed(String message) implements Response {}

  public static String encode(Request request) {
    var map = new LinkedHashMap<String, Object>();
    switch (request) {
      case Fetch ignored -> map.put(OP, OP_FETCH);
      case Commit commit -> {
        map.put(OP, OP_COMMIT);
        map.put(ID, commit.entityId());
        map.put(SNAPSHOT, commit.snapshot());
      }
      case Bye ignored -> map.put(OP, OP_BYE);
    }
    return YamlUtil.dumpJson(map);
  }

  public static String encode(Response response) {
    var map = new LinkedHashMap<String, Object>();
    switch (response) {
      case Fetched fetched -> {
        map.put(ID, fetched.mainId());
        map.put(MAX_SEQ, fetched.maxSeq());
        var entities = new LinkedHashMap<String, Object>();
        fetched
            .entities()
            .forEach(
                (id, snapshot) -> {
                  var entry = new LinkedHashMap<String, Object>();
                  entry.put(REV, snapshot.rev());
                  entry.put(SNAPSHOT, snapshot.snapshot());
                  entities.put(id, entry);
                });
        map.put(ENTITIES, entities);
      }
      case Committed committed -> {
        map.put(REV, committed.rev());
        map.put(MAX_SEQ, committed.maxSeq());
      }
      case Failed failed -> map.put(ERROR, failed.message());
    }
    return YamlUtil.dumpJson(map);
  }

  public static Request decodeRequest(String line) {
    var map = YamlUtil.parseMap(line);
    var op = string(map, OP);
    return switch (op) {
      case OP_FETCH -> new Fetch();
      case OP_COMMIT -> new Commit(string(map, ID), snapshot(map, SNAPSHOT));
      case OP_BYE -> new Bye();
      case null, default -> throw new IllegalArgumentException("Unknown sync op: " + op);
    };
  }

  public static Response decodeResponse(String line) {
    var map = YamlUtil.parseMap(line);
    if (map.containsKey(ERROR)) {
      return new Failed(string(map, ERROR));
    }
    if (map.containsKey(ENTITIES)) {
      return new Fetched(string(map, ID), longValue(map, MAX_SEQ), entities(map));
    }
    if (map.containsKey(REV)) {
      return new Committed(string(map, REV), longValue(map, MAX_SEQ));
    }
    throw new IllegalArgumentException("Unrecognized sync response: " + line);
  }

  private static Map<String, Snapshot> entities(Map<String, Object> map) {
    var entities = new LinkedHashMap<String, Snapshot>();
    var raw = snapshot(map, ENTITIES);
    if (raw != null) {
      raw.forEach((id, value) -> entities.put(id, snapshot(value)));
    }
    return entities;
  }

  @SuppressWarnings("unchecked")
  private static Snapshot snapshot(Object value) {
    var entry = (Map<String, Object>) value;
    return new Snapshot(string(entry, REV), (Map<String, Object>) entry.get(SNAPSHOT));
  }

  private static String string(Map<String, Object> map, String key) {
    var value = map.get(key);
    return value == null ? null : value.toString();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> snapshot(Map<String, Object> map, String key) {
    return (Map<String, Object>) map.get(key);
  }

  private static long longValue(Map<String, Object> map, String key) {
    return map.get(key) instanceof Number n ? n.longValue() : 0L;
  }
}
