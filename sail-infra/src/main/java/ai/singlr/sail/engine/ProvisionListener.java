/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.engine;

/** Callback for reporting progress during multi-step provisioning operations. */
public interface ProvisionListener {

  void onStep(int step, int total, String description);

  void onStepDone(int step, int total, String detail);

  default void onStepSkipped(int step, int total, String detail) {}

  /** A no-op listener that silently discards all events. */
  ProvisionListener NOOP =
      new ProvisionListener() {
        @Override
        public void onStep(int step, int total, String description) {}

        @Override
        public void onStepDone(int step, int total, String detail) {}
      };
}
