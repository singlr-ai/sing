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

    static String render(SyncConflicts.Conflict conflict) {
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

    enum Strategy {
      MINE,
      THEIRS,
      MERGE
    }

    @Override
    public Integer call() throws Exception {
      var strategy = strategy(mine, theirs, merge || mergeFile != null);
      if (strategy == null) {
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
        String edited = null;
        if (strategy == Strategy.MERGE) {
          if (!mergeable(conflict)) {
            System.err.println(
                Banner.errorLine(
                    "--merge is not available for a delete-vs-edit conflict; use --mine or"
                        + " --theirs.",
                    Ansi.AUTO));
            return 1;
          }
          edited = mergeFile != null ? Files.readString(mergeFile) : editInEditor(conflict);
          if (edited == null) {
            return 1;
          }
        }
        var chosen = choose(conflict, strategy, edited);
        apply(new SpecStore(db), conflicts, conflict, chosen, parse(conflict.remoteSnapshot()));
        System.out.println(
            Ansi.AUTO.string(
                "  @|green ✓|@ Resolved @|yellow "
                    + entity
                    + "|@. Run @|bold sail sync|@ to propagate."));
        return 0;
      }
    }

    /** The single chosen strategy, or {@code null} unless exactly one was requested. */
    static Strategy strategy(boolean mine, boolean theirs, boolean merge) {
      if ((mine ? 1 : 0) + (theirs ? 1 : 0) + (merge ? 1 : 0) != 1) {
        return null;
      }
      if (mine) {
        return Strategy.MINE;
      }
      return theirs ? Strategy.THEIRS : Strategy.MERGE;
    }

    /**
     * A field-level merge needs both sides present, so it is offered only outside delete-vs-edit.
     */
    static boolean mergeable(SyncConflicts.Conflict conflict) {
      return parse(conflict.baseSnapshot()) != null
          && parse(conflict.localSnapshot()) != null
          && parse(conflict.remoteSnapshot()) != null;
    }

    /**
     * The snapshot to resolve to; {@code null} is a deletion. {@code edited} is the merged YAML.
     */
    static Map<String, Object> choose(
        SyncConflicts.Conflict conflict, Strategy strategy, String edited) {
      return switch (strategy) {
        case MINE -> parse(conflict.localSnapshot());
        case THEIRS -> parse(conflict.remoteSnapshot());
        case MERGE -> ConflictMerge.parseTemplate(edited);
      };
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

    private String editInEditor(SyncConflicts.Conflict conflict)
        throws IOException, InterruptedException {
      var template =
          ConflictMerge.mergeTemplate(
              parse(conflict.baseSnapshot()),
              parse(conflict.localSnapshot()),
              parse(conflict.remoteSnapshot()),
              conflict.fields());
      var file = Files.createTempFile("sail-merge-", ".yaml");
      try {
        Files.writeString(file, template);
        var editor = System.getenv().getOrDefault("EDITOR", "vi");
        var exit = new ProcessBuilder(editor, file.toString()).inheritIO().start().waitFor();
        if (exit != 0) {
          System.err.println(Banner.errorLine("Editor exited non-zero; aborting.", Ansi.AUTO));
          return null;
        }
        return Files.readString(file);
      } finally {
        Files.deleteIfExists(file);
      }
    }
  }

  static Map<String, Object> parse(String json) {
    return json == null || json.isBlank() ? null : YamlUtil.parseMap(json);
  }
}
