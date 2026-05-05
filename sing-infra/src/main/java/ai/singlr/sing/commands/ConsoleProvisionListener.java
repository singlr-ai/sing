/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.commands;

import ai.singlr.sing.engine.Banner;
import ai.singlr.sing.engine.ProvisionListener;
import picocli.CommandLine.Help.Ansi;

/**
 * Prints styled provisioning progress to stdout using picocli ANSI markup. Shows an animated
 * spinner on the current step while it is running, then replaces it with the final status line.
 */
final class ConsoleProvisionListener implements ProvisionListener {

  static final ConsoleProvisionListener INSTANCE = new ConsoleProvisionListener();

  private static final String[] FRAMES = {
    "\u280b", "\u2819", "\u2839", "\u2838", "\u283c", "\u2834", "\u2826", "\u2827", "\u2807",
    "\u280f"
  };
  private static final long FRAME_MS = 80;

  private volatile Thread spinnerThread;
  private volatile boolean spinning;

  private ConsoleProvisionListener() {}

  @Override
  public void onStep(int step, int total, String description) {
    var prefix = Ansi.AUTO.string("  @|bold [" + step + "/" + total + "]|@ ");
    spinning = true;
    spinnerThread =
        Thread.ofVirtual()
            .name("spinner-" + step)
            .start(
                () -> {
                  var i = 0;
                  while (spinning) {
                    var frame = FRAMES[i % FRAMES.length];
                    System.out.print(
                        "\r"
                            + prefix
                            + Ansi.AUTO.string("@|yellow " + frame + "|@")
                            + " "
                            + description);
                    System.out.flush();
                    try {
                      Thread.sleep(FRAME_MS);
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                      return;
                    }
                    i++;
                  }
                });
  }

  @Override
  public void onStepDone(int step, int total, String detail) {
    stopSpinner();
    System.out.print("\r\033[2K");
    System.out.println(Banner.stepDoneLine(step, total, detail, Ansi.AUTO));
  }

  @Override
  public void onStepSkipped(int step, int total, String detail) {
    stopSpinner();
    System.out.print("\r\033[2K");
    System.out.println(Banner.stepSkippedLine(step, total, detail, Ansi.AUTO));
  }

  private void stopSpinner() {
    spinning = false;
    var t = spinnerThread;
    if (t != null) {
      try {
        t.join(200);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      spinnerThread = null;
    }
  }
}
