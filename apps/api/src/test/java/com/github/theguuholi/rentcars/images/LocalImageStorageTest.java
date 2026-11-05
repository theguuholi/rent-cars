package com.github.theguuholi.rentcars.images;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class LocalImageStorageTest {
  @TempDir Path root;

  @Nested
  class Write {
    @Test
    @DisplayName(
        "Given a new key, when writing and reading across instances, then preserve bytes until idempotent deletion")
    void persistence() throws Exception {
      var storage = new LocalImageStorage(root.resolve("nested"));
      var key = UUID.randomUUID();
      storage.write(key, new byte[] {1, 2, 3});
      assertThat(new LocalImageStorage(root.resolve("nested")).read(key)).containsExactly(1, 2, 3);
      assertThatThrownBy(() -> storage.write(key, new byte[] {4}))
          .isInstanceOf(FileAlreadyExistsException.class);
      storage.delete(key);
      storage.delete(key);
      assertThatThrownBy(() -> storage.read(key)).isInstanceOf(NoSuchFileException.class);
    }
  }

  @Nested
  class Read {
    @Test
    @DisplayName(
        "Given a persisted file, when a fresh storage instance reads it, then return the same bytes")
    void read() throws Exception {
      var key = UUID.randomUUID();
      new LocalImageStorage(root).write(key, new byte[] {7});
      assertThat(new LocalImageStorage(root).read(key)).containsExactly(7);
    }
  }

  @Nested
  class Delete {
    @Test
    @DisplayName("Given a missing key, when deleting repeatedly, then keep the directory empty")
    void delete() throws Exception {
      var key = UUID.randomUUID();
      var storage = new LocalImageStorage(root);
      storage.delete(key);
      storage.delete(key);
      assertThat(root).isEmptyDirectory();
    }
  }
}
