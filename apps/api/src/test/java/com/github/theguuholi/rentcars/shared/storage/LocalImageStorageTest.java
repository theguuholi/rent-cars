package com.github.theguuholi.rentcars.shared.storage;

import static org.assertj.core.api.Assertions.*;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

class LocalImageStorageTest {
  @TempDir Path root;

  @Nested
  class Store {
    @Test
    @DisplayName(
        "Given an unused key, when storing, then directories are created and duplicate writes cannot overwrite it")
    void storesWithoutOverwrite() throws Exception {
      var storage = new LocalImageStorage(root);
      storage.store("cars/1/2.png", new byte[] {1, 2}, "image/png");
      assertThat(Files.readAllBytes(root.resolve("cars/1/2.png"))).containsExactly(1, 2);
      assertThatThrownBy(() -> storage.store("cars/1/2.png", new byte[] {3}, "image/png"))
          .isInstanceOf(UncheckedIOException.class);
      assertThat(storage.load("cars/1/2.png")).containsExactly(1, 2);
    }

    @Test
    @DisplayName("Given an escaping or symlink path, when storing, then storage rejects it")
    void rejectsUnsafePaths() throws Exception {
      var storage = new LocalImageStorage(root);
      for (String key : new String[] {"", ".", "../outside", "/tmp/outside"}) {
        assertThatThrownBy(() -> storage.store(key, new byte[] {1}, "image/png"))
            .isInstanceOf(IllegalArgumentException.class);
      }
      Files.createSymbolicLink(root.resolve("link"), root);
      assertThatThrownBy(() -> storage.store("link/x", new byte[] {1}, "image/png"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class Load {
    @Test
    @DisplayName(
        "Given a missing file or directory, when loading, then missing files are 404 and IO failures remain server errors")
    void errors() throws Exception {
      var storage = new LocalImageStorage(root);
      assertThatThrownBy(() -> storage.load("absent"))
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
      Files.createDirectory(root.resolve("directory"));
      assertThatThrownBy(() -> storage.load("directory")).isInstanceOf(UncheckedIOException.class);
    }
  }

  @Nested
  class Delete {
    @Test
    @DisplayName("Given a stored file, when deleting twice, then deletion is idempotent")
    void idempotent() throws Exception {
      var storage = new LocalImageStorage(root);
      storage.store("a.png", new byte[] {1}, "image/png");
      storage.delete("a.png");
      storage.delete("a.png");
      new LocalImageStorage(Path.of("/")).delete("private/tmp/" + java.util.UUID.randomUUID());
      assertThat(root.resolve("a.png")).doesNotExist();
    }

    @Test
    @DisplayName("Given a nonempty directory, when deleting, then IO failure is reported")
    void failedDelete() throws Exception {
      var storage = new LocalImageStorage(root);
      storage.store("dir/a.png", new byte[] {1}, "image/png");
      assertThatThrownBy(() -> storage.delete("dir")).isInstanceOf(UncheckedIOException.class);
    }
  }
}
