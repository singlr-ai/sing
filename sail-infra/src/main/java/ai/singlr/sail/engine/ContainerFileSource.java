/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.engine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * A project's container workspace as a {@link FileSource}, reached over {@code incus exec} as the
 * dev user. So {@code sail project files add} browses the actual project files where they live — in
 * the container — rather than whatever directory the engineer happens to be standing in.
 *
 * <p>Listings use {@code find -printf} with a tab separator (robust for spaces in names); content
 * is pulled as {@code base64} so binary files survive the text stdout boundary.
 */
public final class ContainerFileSource implements FileSource {

  private final ShellExec shell;
  private final String project;

  public ContainerFileSource(ShellExec shell, String project) {
    this.shell = shell;
    this.project = project;
  }

  @Override
  public List<FilePicker.Entry> children(Path dir) throws IOException {
    var lines =
        run(
            List.of(
                "find",
                dir.toString(),
                "-maxdepth",
                "1",
                "-mindepth",
                "1",
                "-printf",
                "%y\\t%s\\t%f\\n"));
    var entries = new ArrayList<FilePicker.Entry>();
    for (var line : lines.split("\n")) {
      if (line.isBlank()) {
        continue;
      }
      var parts = line.split("\t", 3);
      if (parts.length < 3) {
        continue;
      }
      entries.add(
          new FilePicker.Entry(dir.resolve(parts[2]), "d".equals(parts[0]), parseLong(parts[1])));
    }
    return entries;
  }

  @Override
  public boolean isDirectory(Path path) throws IOException {
    return ok(List.of("test", "-d", path.toString()));
  }

  @Override
  public long size(Path file) throws IOException {
    return parseLong(run(List.of("stat", "-c", "%s", file.toString())).strip());
  }

  @Override
  public List<Path> walkFiles(Path dir) throws IOException {
    var args = new ArrayList<>(List.of("find", dir.toString(), "("));
    var first = true;
    for (var junk : FilePicker.IGNORED_DIRS) {
      if (!first) {
        args.add("-o");
      }
      args.add("-name");
      args.add(junk);
      first = false;
    }
    args.addAll(List.of(")", "-prune", "-o", "-type", "f", "-print"));
    var files = new ArrayList<Path>();
    for (var line : run(args).split("\n")) {
      if (!line.isBlank()) {
        files.add(Path.of(line));
      }
    }
    return files;
  }

  @Override
  public byte[] read(Path file) throws IOException {
    return Base64.getMimeDecoder().decode(run(List.of("base64", file.toString())).strip());
  }

  private static long parseLong(String value) {
    try {
      return Long.parseLong(value.strip());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private String run(List<String> args) throws IOException {
    var result = exec(args);
    if (!result.ok()) {
      throw new IOException(project + ": " + result.stderr().strip());
    }
    return result.stdout();
  }

  private boolean ok(List<String> args) throws IOException {
    return exec(args).ok();
  }

  private ShellExec.Result exec(List<String> args) throws IOException {
    try {
      return shell.exec(ContainerExec.asDevUser(project, args));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while reading the container workspace", e);
    } catch (TimeoutException e) {
      throw new IOException("Timed out reading the container workspace", e);
    }
  }
}
