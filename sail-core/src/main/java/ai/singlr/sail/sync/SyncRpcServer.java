/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.sync;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * Main's side of one sync session: a stateless request loop over the SSH channel's stdio. It serves
 * a node's {@link SyncWire.Fetch} from the authoritative {@link MainReplica}, mints a rev for each
 * accepted {@link SyncWire.Commit}, and returns at {@link SyncWire.Bye} or end of stream. The
 * {@code writable} gate is the push half of Door-2 authorization: a {@code viewer} opens a session
 * and pulls, but its commits are refused so only {@code member}+ work propagates to the shared
 * board.
 */
public final class SyncRpcServer {

  private final MainReplica main;
  private final boolean writable;

  public SyncRpcServer(MainReplica main, boolean writable) {
    this.main = Objects.requireNonNull(main, "main");
    this.writable = writable;
  }

  public void serve(BufferedReader in, Writer out) throws IOException {
    for (var line = in.readLine(); line != null; line = in.readLine()) {
      var request = SyncWire.decodeRequest(line);
      if (request instanceof SyncWire.Bye) {
        return;
      }
      out.write(SyncWire.encode(respond(request)));
      out.write('\n');
      out.flush();
    }
  }

  private SyncWire.Response respond(SyncWire.Request request) {
    return switch (request) {
      case SyncWire.Fetch ignored -> fetched();
      case SyncWire.Commit commit -> committed(commit);
      case SyncWire.Bye ignored -> throw new IllegalStateException("bye is handled by the loop");
    };
  }

  private SyncWire.Fetched fetched() {
    var entities = new LinkedHashMap<String, SyncWire.Snapshot>();
    for (var id : main.entityIds()) {
      entities.put(id, new SyncWire.Snapshot(main.currentRev(id), main.current(id)));
    }
    return new SyncWire.Fetched(main.id(), main.maxSeq(), entities);
  }

  private SyncWire.Response committed(SyncWire.Commit commit) {
    if (!writable) {
      return new SyncWire.Failed(
          "Your role is read-only: it can pull the shared board but not push changes.");
    }
    var rev = main.commit(commit.entityId(), commit.snapshot());
    return new SyncWire.Committed(rev, main.maxSeq());
  }
}
