/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Thin wrapper around {@link ProcessBuilder} that implements {@link ShellExec}. Supports dry-run
 * mode (prints commands instead of executing) and configurable timeouts.
 */
public final class ShellExecutor implements ShellExec {

  private final boolean dryRun;
  private final Duration defaultTimeout;

  public ShellExecutor(boolean dryRun, Duration defaultTimeout) {
    this.dryRun = dryRun;
    this.defaultTimeout = defaultTimeout;
  }

  public ShellExecutor(boolean dryRun) {
    this(dryRun, Duration.ofSeconds(120));
  }

  @Override
  public Result exec(List<String> command)
      throws IOException, InterruptedException, TimeoutException {
    return exec(command, null, defaultTimeout);
  }

  @Override
  public Result exec(List<String> command, Path workDir, Duration timeout)
      throws IOException, InterruptedException, TimeoutException {
    if (dryRun) {
      return dryRunResult(command);
    }

    var pb = new ProcessBuilder(command);
    pb.redirectErrorStream(false);
    if (workDir != null) {
      pb.directory(workDir.toFile());
    }

    var process = pb.start();
    process.getOutputStream().close();

    var stderrFuture = CompletableFuture.supplyAsync(() -> readFully(process.getErrorStream()));
    String stdout;
    String stderr;
    try (var stdoutStream = process.getInputStream()) {
      stdout = new String(stdoutStream.readAllBytes(), StandardCharsets.UTF_8);
    }
    stderr = stderrFuture.join();

    var finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
    if (!finished) {
      process.destroyForcibly();
      throw new TimeoutException(
          "Command timed out after " + timeout.toSeconds() + "s: " + String.join(" ", command));
    }

    return new Result(process.exitValue(), stdout, stderr);
  }

  @Override
  public boolean isDryRun() {
    return dryRun;
  }

  private static String readFully(InputStream in) {
    try (in) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      return "";
    }
  }

  private static Result dryRunResult(List<String> command) {
    System.out.println("[dry-run] " + String.join(" ", command));
    return new Result(0, "", "");
  }
}
