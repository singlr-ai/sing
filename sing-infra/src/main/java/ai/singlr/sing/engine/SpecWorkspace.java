/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.engine;

import ai.singlr.sing.config.Spec;
import ai.singlr.sing.config.SpecDirectory;
import ai.singlr.sing.config.YamlUtil;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

public final class SpecWorkspace {

  private final ShellExec shell;
  private final String containerName;
  private final String specsDir;

  public SpecWorkspace(ShellExec shell, String containerName, String specsDir) {
    this.shell = Objects.requireNonNull(shell);
    NameValidator.requireValidProjectName(containerName);
    if (specsDir == null || specsDir.isBlank()) {
      throw new IllegalArgumentException("specsDir is required.");
    }
    this.containerName = containerName;
    this.specsDir = specsDir;
  }

  public List<Spec> readIndex() throws IOException, InterruptedException, TimeoutException {
    var result = shell.exec(ContainerExec.asDevUser(containerName, List.of("cat", indexPath())));
    if (!result.ok()) {
      if (isMissingFile(result)) {
        return List.of();
      }
      throw new IOException("Failed to read spec index: " + result.stderr());
    }
    if (result.stdout().isBlank()) {
      return List.of();
    }
    return SpecDirectory.parseIndex(YamlUtil.parseMap(result.stdout()));
  }

  public Spec readSpec(String specId) throws IOException, InterruptedException, TimeoutException {
    NameValidator.requireValidSpecId(specId);
    return SpecDirectory.findById(readIndex(), specId);
  }

  public String readSpecBody(String specId)
      throws IOException, InterruptedException, TimeoutException {
    NameValidator.requireValidSpecId(specId);
    var result =
        shell.exec(
            ContainerExec.asDevUser(containerName, List.of("cat", specMarkdownPath(specId))));
    if (result.ok()) {
      return result.stdout().strip();
    }
    if (isMissingFile(result)) {
      return null;
    }
    throw new IOException("Failed to read spec markdown: " + result.stderr());
  }

  public void writeIndex(List<Spec> specs)
      throws IOException, InterruptedException, TimeoutException {
    ensureSpecsRoot();
    var yaml = YamlUtil.dumpToString(SpecDirectory.generateIndex(specs));
    var result =
        shell.exec(
            ContainerExec.asDevUser(
                containerName,
                List.of("bash", "-c", "printf '%s' \"$1\" > \"$2\"", "bash", yaml, indexPath())));
    if (!result.ok()) {
      throw new IOException("Failed to write spec index: " + result.stderr());
    }
  }

  public Spec updateStatus(String specId, String newStatus)
      throws IOException, InterruptedException, TimeoutException {
    var specs = readIndex();
    var updated = SpecDirectory.updateStatus(specs, specId, newStatus);
    writeIndex(updated);
    return SpecDirectory.findById(updated, specId);
  }

  public void createSpec(Spec spec, String markdown)
      throws IOException, InterruptedException, TimeoutException {
    NameValidator.requireValidSpecId(spec.id());
    var specs = readIndex();
    if (SpecDirectory.findById(specs, spec.id()) != null || specDirectoryExists(spec.id())) {
      throw new IllegalArgumentException("Spec '" + spec.id() + "' already exists.");
    }
    ensureSpecDirectory(spec.id());
    writeSpecBody(spec.id(), markdown);
    writeIndex(append(specs, spec));
  }

  public String specMarkdownPath(String specId) {
    NameValidator.requireValidSpecId(specId);
    return specsDir + "/" + specId + "/spec.md";
  }

  private void ensureSpecsRoot() throws IOException, InterruptedException, TimeoutException {
    var result =
        shell.exec(ContainerExec.asDevUser(containerName, List.of("mkdir", "-p", specsDir)));
    if (!result.ok()) {
      throw new IOException("Failed to create specs directory: " + result.stderr());
    }
  }

  private void ensureSpecDirectory(String specId)
      throws IOException, InterruptedException, TimeoutException {
    var result =
        shell.exec(
            ContainerExec.asDevUser(
                containerName, List.of("mkdir", "-p", specsDir + "/" + specId)));
    if (!result.ok()) {
      throw new IOException("Failed to create spec directory: " + result.stderr());
    }
  }

  private void writeSpecBody(String specId, String markdown)
      throws IOException, InterruptedException, TimeoutException {
    var result =
        shell.exec(
            ContainerExec.asDevUser(
                containerName,
                List.of(
                    "bash",
                    "-c",
                    "printf '%s' \"$1\" > \"$2\"",
                    "bash",
                    Objects.requireNonNullElse(markdown, ""),
                    specMarkdownPath(specId))));
    if (!result.ok()) {
      throw new IOException("Failed to write spec markdown: " + result.stderr());
    }
  }

  private boolean specDirectoryExists(String specId)
      throws IOException, InterruptedException, TimeoutException {
    var result =
        shell.exec(
            ContainerExec.asDevUser(containerName, List.of("test", "-e", specsDir + "/" + specId)));
    return result.ok();
  }

  private String indexPath() {
    return specsDir + "/index.yaml";
  }

  private static List<Spec> append(List<Spec> specs, Spec spec) {
    return java.util.stream.Stream.concat(specs.stream(), java.util.stream.Stream.of(spec))
        .toList();
  }

  private static boolean isMissingFile(ShellExec.Result result) {
    return result.stderr() != null && result.stderr().contains("No such file");
  }
}
