/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.sync;

/**
 * A sync session failed at the transport layer — main refused a request (e.g. a read-only push) or
 * the channel returned something the protocol does not allow. Distinct from {@link
 * java.io.UncheckedIOException}, which signals the channel itself broke.
 */
public final class SyncTransportException extends RuntimeException {

  public SyncTransportException(String message) {
    super(message);
  }
}
