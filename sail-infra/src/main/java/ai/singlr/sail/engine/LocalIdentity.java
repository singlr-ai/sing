/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.engine;

import ai.singlr.sail.common.Strings;
import ai.singlr.sail.config.PlaceholderResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Answers a project definition's personal-field placeholders from <em>this</em> box: the git
 * identity from {@code git config --global}, and {@code ${SSH_PUBLIC_KEY}} from the box's sail
 * identity key — the same key {@code sail join} registers with main. Resolution happens once, when
 * a project is provisioned, so the synced definition stays identity-free and each engineer's
 * containers commit as them and trust only their own key. A missing value fails loud with the one
 * command that fixes it, rather than silently provisioning with someone else's identity.
 */
public final class LocalIdentity {

  private final ShellExec shell;
  private final Path sshPublicKeyPath;

  public LocalIdentity(ShellExec shell, Path sshPublicKeyPath) {
    this.shell = shell;
    this.sshPublicKeyPath = sshPublicKeyPath;
  }

  /** This box's identity: real git config and the sail sync public key. */
  public static LocalIdentity detect() {
    return new LocalIdentity(new ShellExecutor(false), SailPaths.syncPublicKeyPath());
  }

  /** Resolves one known placeholder name to this box's value, failing loud if it is not set. */
  public String valueFor(String placeholder) {
    return switch (placeholder) {
      case PlaceholderResolver.GIT_NAME -> gitConfig("user.name");
      case PlaceholderResolver.GIT_EMAIL -> gitConfig("user.email");
      case PlaceholderResolver.SSH_PUBLIC_KEY -> sshPublicKey();
      default ->
          throw new IllegalArgumentException("No box-local source for ${" + placeholder + "}");
    };
  }

  private String gitConfig(String key) {
    var value = run(List.of("git", "config", "--global", "--get", key));
    if (Strings.isBlank(value)) {
      throw new IllegalStateException(
          "This box has no git "
              + key
              + " set, so a project here can't take on your identity.\n"
              + "  Set it once and re-run: git config --global "
              + key
              + " \"...\"");
    }
    return value;
  }

  private String sshPublicKey() {
    try {
      var key = Files.readString(sshPublicKeyPath).strip();
      if (Strings.isBlank(key)) {
        throw new IllegalStateException(missingKeyMessage());
      }
      return key;
    } catch (IOException e) {
      throw new IllegalStateException(missingKeyMessage());
    }
  }

  private String missingKeyMessage() {
    return "This box has no sail identity key yet ("
        + sshPublicKeyPath
        + ").\n  It is created when you run 'sail join' or 'sail host ssh-identity'.";
  }

  private String run(List<String> command) {
    try {
      var result = shell.exec(command);
      return result.ok() ? result.stdout().strip() : "";
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return "";
    } catch (IOException | TimeoutException e) {
      return "";
    }
  }
}
