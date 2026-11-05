package com.github.theguuholi.rentcars.images;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;

class ImageCleanupTest {
  final ImageObjectRepository objects = mock();
  final ImageStorage storage = mock();
  final ImageCleanup cleanup = new ImageCleanup(objects, storage);

  @Nested
  class Clean {
    @Test
    @DisplayName(
        "Given a still-referenced image, when rechecking cleanup candidates, then never delete its stored bytes")
    void reference() {
      var key = UUID.randomUUID();
      when(objects.lockOrphans()).thenReturn(List.of(new ImageObject(key)));
      when(objects.deleteUnreferenced(key)).thenReturn(0);
      cleanup.clean();
      verifyNoInteractions(storage);
    }

    @Test
    @DisplayName(
        "Given an orphan, when deleting succeeds, then remove its exact immutable storage key")
    void orphan() throws Exception {
      var key = UUID.randomUUID();
      when(objects.lockOrphans()).thenReturn(List.of(new ImageObject(key)));
      when(objects.deleteUnreferenced(key)).thenReturn(1);
      cleanup.clean();
      verify(storage).delete(key);
    }

    @Test
    @DisplayName(
        "Given a storage failure, when cleaning, then propagate an unchecked error so Spring rolls back the allocation deletion")
    void failure() throws Exception {
      var key = UUID.randomUUID();
      when(objects.lockOrphans()).thenReturn(List.of(new ImageObject(key)));
      when(objects.deleteUnreferenced(key)).thenReturn(1);
      doThrow(new IOException("offline")).when(storage).delete(key);
      assertThatThrownBy(cleanup::clean).isInstanceOf(UncheckedIOException.class);
    }
  }
}
