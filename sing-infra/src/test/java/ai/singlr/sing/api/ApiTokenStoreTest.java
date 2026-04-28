/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.sing.api;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiTokenStoreTest {

  @TempDir Path tempDir;

  @Test
  void createsTokenWhenMissing() throws Exception {
    var path = tempDir.resolve("api-token");
    var store = new ApiTokenStore(path, new SecureRandom(new byte[] {1, 2, 3}));

    var token = store.readOrCreate();

    assertFalse(token.isBlank());
    assertEquals(token, Files.readString(path).strip());
    assertEquals(path, store.path());
  }

  @Test
  void reusesExistingToken() throws Exception {
    var path = tempDir.resolve("api-token");
    Files.writeString(path, "known-token\n");
    var store = new ApiTokenStore(path, new SecureRandom());

    assertEquals("known-token", store.readOrCreate());
  }

  @Test
  void rejectsBlankExistingToken() throws Exception {
    var path = tempDir.resolve("api-token");
    Files.writeString(path, "\n");
    var store = new ApiTokenStore(path, new SecureRandom());

    var error = assertThrows(java.io.IOException.class, store::readOrCreate);

    assertTrue(error.getMessage().contains("empty"));
  }

  @Test
  void restrictsExistingTokenBeforeReuse() throws Exception {
    var path = tempDir.resolve("api-token");
    Files.writeString(path, "known-token\n");
    var calls = new AtomicInteger();
    var store = new ApiTokenStore(path, new SecureRandom(), ignored -> calls.incrementAndGet());

    assertEquals("known-token", store.readOrCreate());
    assertEquals(1, calls.get());
  }

  @Test
  void rejectsDirectoryTokenPath() throws Exception {
    var path = tempDir.resolve("api-token");
    Files.createDirectory(path);
    var store = new ApiTokenStore(path, new SecureRandom());

    var error = assertThrows(java.io.IOException.class, store::readOrCreate);

    assertTrue(error.getMessage().contains("regular file"));
  }

  @Test
  void defaultStoreUsesSingDirectory() {
    assertTrue(ApiTokenStore.defaultStore().path().toString().endsWith("api-token"));
  }

  @Test
  void unsupportedPermissionsAreIgnored() throws Exception {
    var path = tempDir.resolve("api-token");
    var store =
        new ApiTokenStore(
            path,
            new SecureRandom(new byte[] {1}),
            ignored -> {
              throw new UnsupportedOperationException("not posix");
            });

    var token = store.readOrCreate();

    assertFalse(token.isBlank());
  }
}
