/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sail.api;

import ai.singlr.sail.engine.SailPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

public final class ApiTokenStore {

  private final Path path;
  private final SecureRandom random;
  private final OwnerPermissionSetter permissionSetter;

  public ApiTokenStore(Path path, SecureRandom random) {
    this(path, random, ApiTokenStore::setOwnerReadOnly);
  }

  ApiTokenStore(Path path, SecureRandom random, OwnerPermissionSetter permissionSetter) {
    this.path = path;
    this.random = random;
    this.permissionSetter = permissionSetter;
  }

  public static ApiTokenStore defaultStore() {
    return new ApiTokenStore(SailPaths.sailDir().resolve("api-token"), new SecureRandom());
  }

  public Path path() {
    return path;
  }

  public String readOrCreate() throws IOException {
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      requireRegularTokenFile();
      restrictOwner();
      var token = Files.readString(path).strip();
      if (token.isBlank()) {
        throw new IOException("API token file is empty: " + path);
      }
      return token;
    }
    Files.createDirectories(path.getParent());
    var bytes = new byte[32];
    random.nextBytes(bytes);
    var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    createTokenFile(token);
    restrictOwner();
    return token;
  }

  private void requireRegularTokenFile() throws IOException {
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("API token path must be a regular file: " + path);
    }
  }

  private void createTokenFile(String token) throws IOException {
    var permissions = Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    Files.createFile(path, PosixFilePermissions.asFileAttribute(permissions));
    Files.writeString(path, token + System.lineSeparator(), StandardOpenOption.WRITE);
  }

  private void restrictOwner() throws IOException {
    try {
      permissionSetter.restrict(path);
    } catch (UnsupportedOperationException ignored) {
    }
  }

  private static void setOwnerReadOnly(Path path) throws IOException {
    Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ));
  }

  @FunctionalInterface
  interface OwnerPermissionSetter {
    void restrict(Path path) throws IOException;
  }
}
