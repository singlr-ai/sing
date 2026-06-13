/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.commands;

import ai.singlr.sail.config.YamlUtil;
import ai.singlr.sail.engine.Banner;
import ai.singlr.sail.engine.HostInfo;
import ai.singlr.sail.engine.SailPaths;
import ai.singlr.sail.engine.SshSyncChannel;
import ai.singlr.sail.store.ChangeLog;
import ai.singlr.sail.store.SpecStore;
import ai.singlr.sail.store.Sqlite;
import ai.singlr.sail.store.SyncConflicts;
import ai.singlr.sail.store.SyncState;
import ai.singlr.sail.sync.RemoteMainReplica;
import ai.singlr.sail.sync.SpecReplica;
import ai.singlr.sail.sync.SyncEngine;
import java.util.LinkedHashMap;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Option;

/**
 * Reconciles this box's local spec replica with the main devbox over the SSH-key gateway. The
 * engine runs here on the node and drives a {@link RemoteMainReplica} across the channel:
 * local-only work pushes (main mints the rev), main-only work pulls, disjoint edits auto-merge, and
 * same-field conflicts are parked locally for {@code sail conflicts} — the node's row is never
 * clobbered. The round is idempotent; running it again after it converges does nothing.
 */
@Command(
    name = "sync",
    description = "Reconcile this box's specs with the main devbox.",
    mixinStandardHelpOptions = true)
public final class SyncCommand implements Callable<Integer> {

  @Option(
      names = "--main",
      required = true,
      description = "SSH target of the main devbox, e.g. sail@maindevbox.")
  private String main;

  @Option(names = "--json", description = "Output the sync report as JSON.")
  private boolean json;

  @Override
  public Integer call() throws Exception {
    try (var db = Sqlite.open(SailPaths.controlPlaneDb())) {
      var local =
          new SpecReplica(
              HostInfo.hostname(),
              new SpecStore(db),
              new ChangeLog(db),
              new SyncConflicts(db),
              new SyncState(db));
      var report = reconcile(local);
      System.out.println(render(report, json));
      return 0;
    }
  }

  private SyncEngine.Report reconcile(SpecReplica local) throws Exception {
    try (var channel = SshSyncChannel.open(main);
        var remote = new RemoteMainReplica(channel.reader(), channel.writer())) {
      return new SyncEngine().reconcile(local, remote);
    }
  }

  static String render(SyncEngine.Report report, boolean json) {
    if (json) {
      var map = new LinkedHashMap<String, Object>();
      map.put("pulled", report.pulled());
      map.put("pushed", report.pushed());
      map.put("merged", report.merged());
      map.put("conflicts", report.conflicts());
      return YamlUtil.dumpJson(map);
    }
    if (report.total() == 0) {
      return Ansi.AUTO.string("  @|green ✓|@ Already in sync with main.");
    }
    var summary =
        Ansi.AUTO.string(
            "  @|green ✓|@ Synced with main: @|bold "
                + report.pulled()
                + "|@ pulled, @|bold "
                + report.pushed()
                + "|@ pushed, @|bold "
                + report.merged()
                + "|@ merged.");
    if (report.conflicts() == 0) {
      return summary;
    }
    return summary
        + "\n"
        + Banner.errorLine(
            report.conflicts()
                + " conflict(s) need your decision. Run 'sail conflicts' to resolve.",
            Ansi.AUTO);
  }
}
