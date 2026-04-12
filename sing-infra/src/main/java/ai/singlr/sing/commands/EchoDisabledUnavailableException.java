/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

final class EchoDisabledUnavailableException extends RuntimeException {

  EchoDisabledUnavailableException() {
    super(
        "Cannot read secret with echo disabled (System.console() is unavailable). "
            + "This can happen when running sing through certain SSH thin clients "
            + "or non-TTY environments. "
            + "Provide the secret non-interactively instead.");
  }
}
