/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.store;

import java.util.List;

/**
 * Manages SQLite schema versioning. Each migration runs inside a transaction. Migrations are
 * idempotent: re-running on a current database is a no-op.
 */
public final class SchemaManager {

  private static final List<String> MIGRATIONS =
      List.of(
          """
          CREATE TABLE IF NOT EXISTS schema_version (
              version INTEGER PRIMARY KEY,
              applied_at TEXT NOT NULL
          )""",
          """
          CREATE TABLE IF NOT EXISTS specs (
              id TEXT PRIMARY KEY,
              title TEXT NOT NULL,
              status TEXT NOT NULL DEFAULT 'draft'
                  CHECK (status IN ('draft', 'pending', 'in_progress', 'review', 'done', 'archived')),
              assignee TEXT,
              agent TEXT,
              model TEXT,
              reasoning_effort TEXT
                  CHECK (reasoning_effort IS NULL OR reasoning_effort IN ('none', 'low', 'medium', 'high', 'xhigh')),
              branch TEXT,
              priority INTEGER NOT NULL DEFAULT 0,
              created_by TEXT,
              created_at TEXT NOT NULL,
              updated_at TEXT NOT NULL
          )""",
          """
          CREATE TABLE IF NOT EXISTS spec_dependencies (
              spec_id TEXT NOT NULL REFERENCES specs(id) ON DELETE CASCADE,
              depends_on TEXT NOT NULL REFERENCES specs(id),
              PRIMARY KEY (spec_id, depends_on)
          )""",
          """
          CREATE TABLE IF NOT EXISTS spec_repos (
              spec_id TEXT NOT NULL REFERENCES specs(id) ON DELETE CASCADE,
              repo TEXT NOT NULL,
              PRIMARY KEY (spec_id, repo)
          )""",
          """
          CREATE TABLE IF NOT EXISTS spec_content (
              spec_id TEXT PRIMARY KEY REFERENCES specs(id) ON DELETE CASCADE,
              body TEXT NOT NULL DEFAULT '',
              plan TEXT NOT NULL DEFAULT '',
              updated_at TEXT NOT NULL
          )""",
          """
          CREATE TABLE IF NOT EXISTS spec_attachments (
              id TEXT PRIMARY KEY,
              spec_id TEXT NOT NULL REFERENCES specs(id) ON DELETE CASCADE,
              filename TEXT NOT NULL,
              content_type TEXT NOT NULL,
              size_bytes INTEGER NOT NULL,
              storage_path TEXT NOT NULL,
              created_at TEXT NOT NULL
          )""",
          """
          CREATE TABLE IF NOT EXISTS events (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              timestamp TEXT NOT NULL,
              type TEXT NOT NULL,
              project TEXT,
              spec_id TEXT,
              agent TEXT,
              host TEXT NOT NULL,
              data TEXT NOT NULL DEFAULT '{}'
          )""",
          "CREATE INDEX IF NOT EXISTS idx_events_type ON events(type)",
          "CREATE INDEX IF NOT EXISTS idx_events_project ON events(project)",
          "CREATE INDEX IF NOT EXISTS idx_events_spec ON events(spec_id)",
          "CREATE INDEX IF NOT EXISTS idx_events_timestamp ON events(timestamp DESC)",
          """
          CREATE TABLE IF NOT EXISTS api_tokens (
              token_hash TEXT PRIMARY KEY,
              name TEXT NOT NULL UNIQUE,
              role TEXT NOT NULL DEFAULT 'member'
                  CHECK (role IN ('admin', 'member')),
              created_at TEXT NOT NULL,
              last_used_at TEXT
          )""");

  private final Sqlite db;

  public SchemaManager(Sqlite db) {
    this.db = db;
  }

  public void migrate() {
    db.execute(MIGRATIONS.getFirst());
    var current = currentVersion();
    for (var i = current; i < MIGRATIONS.size(); i++) {
      var version = i + 1;
      var sql = MIGRATIONS.get(i);
      db.transaction(
          () -> {
            db.execute(sql);
            db.execute(
                "INSERT INTO schema_version (version, applied_at) VALUES (?, datetime('now'))",
                version);
          });
    }
  }

  public int currentVersion() {
    try {
      return db.queryOne("SELECT MAX(version) FROM schema_version", row -> (int) row.integer(0))
          .orElse(0);
    } catch (SqliteException e) {
      return 0;
    }
  }
}
