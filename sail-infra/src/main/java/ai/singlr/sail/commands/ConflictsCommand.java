/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.commands;

import ai.singlr.sail.config.YamlUtil;
import ai.singlr.sail.engine.Banner;
import ai.singlr.sail.engine.SailPaths;
import ai.singlr.sail.store.SpecStore;
import ai.singlr.sail.store.Sqlite;
import ai.singlr.sail.store.SyncConflicts;
import ai.singlr.sail.sync.ConflictMerge;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Lists and resolves the sync conflicts parked on this box. A conflict is opened only when a remote
 * change clashes with a local edit on the same field (or a delete races an edit); the local row is
 * never touched while it is open. {@code resolve} rebases the row onto main's version and writes
 * the chosen value, so a follow-up {@code sail sync} converges and the conflict cannot re-raise —
 * and every version stays in the change log, so no choice is destructive.
 */
@Command(
    name = "conflicts",
    description = "List and resolve sync conflicts.",
    mixinStandardHelpOptions = true,
    subcommands = {ConflictsCommand.Show.class, ConflictsCommand.Resolve.class})
public final class ConflictsCommand implements Callable<Integer> {

  static final String ENTITY = "spec";

  @Option(names = "--json", description = "Output the pending conflicts as JSON.")
  private boolean json;

  @Override
  public Integer call() {
    try (var db = Sqlite.open(SailPaths.controlPlaneDb())) {
      var pending = new SyncConflicts(db).pending();
      System.out.println(renderList(pending, json));
      return 0;
    }
  }

  static String renderList(List<SyncConflicts.Conflict> pending, boolean json) {
    if (json) {
      var rows =
          pending.stream()
              .map(
                  c -> {
                    var row = new LinkedHashMap<String, Object>();
                    row.put("entity", c.entityId());
                    row.put("fields", c.fields());
                    row.put("detected_at", c.detectedAt());
                    return (Object) row;
                  })
              .toList();
      return YamlUtil.dumpJson(rows);
    }
    if (pending.isEmpty()) {
      return Ansi.AUTO.string("  @|green ✓|@ No conflicts.");
    }
    var out = new StringBuilder();
    out.append(
        Ansi.AUTO.string("  @|bold " + pending.size() + "|@ conflict(s) need your decision:\n"));
    for (var c : pending) {
      out.append(
          Ansi.AUTO.string(
              "    @|yellow "
                  + c.entityId()
                  + "|@ — fields: "
                  + String.join(", ", c.fields())
                  + " @|faint ("
                  + c.detectedAt()
                  + ")|@\n"));
    }
    out.append(Ansi.AUTO.string("  Run @|bold sail conflicts show <id>|@ to inspect, "));
    out.append(Ansi.AUTO.string("@|bold sail conflicts resolve <id> --mine|--theirs|--merge|@."));
    return out.toString();
  }

  @Command(
      name = "show",
      description = "Show the field-level diff of a conflict.",
      mixinStandardHelpOptions = true)
  static final class Show implements Callable<Integer> {

    @Parameters(index = "0", description = "Spec id of the conflict.")
    private String entity;

    @Override
    public Integer call() {
      try (var db = Sqlite.open(SailPaths.controlPlaneDb())) {
        var conflict = new SyncConflicts(db).pendingFor(ENTITY, entity).orElse(null);
        if (conflict == null) {
          System.err.println(
              Banner.errorLine("No open conflict for spec '" + entity + "'.", Ansi.AUTO));
          return 1;
        }
        System.out.println(render(conflict));
        return 0;
      }
    }

    private static String render(SyncConflicts.Conflict conflict) {
      var diff =
          ConflictMerge.diff(
              parse(conflict.baseSnapshot()),
              parse(conflict.localSnapshot()),
              parse(conflict.remoteSnapshot()),
              conflict.fields());
      var out = new StringBuilder();
      out.append(Ansi.AUTO.string("  Conflict on @|yellow " + conflict.entityId() + "|@:\n"));
      for (var change : diff) {
        var marker =
            change.clash() ? Ansi.AUTO.string("@|red ✗|@") : Ansi.AUTO.string("@|green ·|@");
        out.append("    ").append(marker).append(' ').append(change.field()).append('\n');
        out.append("        base:   ").append(ConflictMerge.render(change.base())).append('\n');
        out.append("        mine:   ").append(ConflictMerge.render(change.mine())).append('\n');
        out.append("        theirs: ").append(ConflictMerge.render(change.theirs())).append('\n');
      }
      return out.toString().stripTrailing();
    }
  }

  @Command(
      name = "resolve",
      description = "Resolve a conflict: --mine, --theirs, or --merge.",
      mixinStandardHelpOptions = true)
  static final class Resolve implements Callable<Integer> {

    @Parameters(index = "0", description = "Spec id of the conflict.")
    private String entity;

    @Option(names = "--mine", description = "Keep this box's version.")
    private boolean mine;

    @Option(names = "--theirs", description = "Adopt main's version (yours stays in history).")
    private boolean theirs;

    @Option(names = "--merge", description = "Merge in $EDITOR, pre-filled with a 3-way merge.")
    private boolean merge;

    @Option(names = "--merge-file", description = "Read the merged record from a file (no editor).")
    private Path mergeFile;

    @Override
    public Integer call() throws Exception {
      var merging = merge || mergeFile != null;
      var chosenStrategies = (mine ? 1 : 0) + (theirs ? 1 : 0) + (merging ? 1 : 0);
      if (chosenStrategies != 1) {
        System.err.println(
            Banner.errorLine("Choose exactly one of --mine, --theirs, or --merge.", Ansi.AUTO));
        return 1;
      }
      try (var db = Sqlite.open(SailPaths.controlPlaneDb())) {
        var conflicts = new SyncConflicts(db);
        var conflict = conflicts.pendingFor(ENTITY, entity).orElse(null);
        if (conflict == null) {
          System.err.println(
              Banner.errorLine("No open conflict for spec '" + entity + "'.", Ansi.AUTO));
          return 1;
        }
        var remote = parse(conflict.remoteSnapshot());
        var chosen = chosen(conflict, remote);
        if (chosen == ABSENT) {
          return 1;
        }
        apply(new SpecStore(db), conflicts, conflict, cast(chosen), remote);
        System.out.println(
            Ansi.AUTO.string(
                "  @|green ✓|@ Resolved @|yellow "
                    + entity
                    + "|@. Run @|bold sail sync|@ to propagate."));
        return 0;
      }
    }

    static String apply(
        SpecStore specs,
        SyncConflicts conflicts,
        SyncConflicts.Conflict conflict,
        Map<String, Object> chosen,
        Map<String, Object> remote) {
      var rev = specs.resolveConflict(conflict.entityId(), chosen, remote);
      conflicts.resolve(conflict.id(), rev);
      return rev;
    }

    private Object chosen(SyncConflicts.Conflict conflict, Map<String, Object> remote)
        throws IOException, InterruptedException {
      if (theirs) {
        return remote;
      }
      var local = parse(conflict.localSnapshot());
      if (mine) {
        return local;
      }
      var base = parse(conflict.baseSnapshot());
      if (base == null || local == null || remote == null) {
        System.err.println(
            Banner.errorLine(
                "--merge is not available for a delete-vs-edit conflict; use --mine or --theirs.",
                Ansi.AUTO));
        return ABSENT;
      }
      if (mergeFile != null) {
        return ConflictMerge.parseTemplate(Files.readString(mergeFile));
      }
      return mergeInEditor(base, local, remote, conflict.fields());
    }

    private Object mergeInEditor(
        Map<String, Object> base,
        Map<String, Object> local,
        Map<String, Object> remote,
        List<String> fields)
        throws IOException, InterruptedException {
      var template = ConflictMerge.mergeTemplate(base, local, remote, fields);
      var file = Files.createTempFile("sail-merge-", ".yaml");
      try {
        Files.writeString(file, template);
        var editor = System.getenv().getOrDefault("EDITOR", "vi");
        var exit = new ProcessBuilder(editor, file.toString()).inheritIO().start().waitFor();
        if (exit != 0) {
          System.err.println(Banner.errorLine("Editor exited non-zero; aborting.", Ansi.AUTO));
          return ABSENT;
        }
        return ConflictMerge.parseTemplate(Files.readString(file));
      } finally {
        Files.deleteIfExists(file);
      }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object snapshot) {
      return (Map<String, Object>) snapshot;
    }

    private static final Object ABSENT = new Object();
  }

  static Map<String, Object> parse(String json) {
    return json == null || json.isBlank() ? null : YamlUtil.parseMap(json);
  }
}
