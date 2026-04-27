/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.api;

import ai.singlr.sing.engine.SingPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
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
    return new ApiTokenStore(SingPaths.singDir().resolve("api-token"), new SecureRandom());
  }

  public Path path() {
    return path;
  }

  public String readOrCreate() throws IOException {
    if (Files.exists(path)) {
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
    Files.writeString(path, token + System.lineSeparator());
    restrictOwner();
    return token;
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
