package com.github.theguuholi.rentcars.avatar;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.cloud.storage.*;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

class GcsImageStorageTest {
  final Storage client = mock(Storage.class);
  final UUID key = UUID.randomUUID();
  final ImageStorage storage = new GcsImageStorage(client, "private-avatars");
  final BlobId id = BlobId.of("private-avatars", key + ".png");

  @Nested
  class Write {
    @Test
    @DisplayName(
        "Given normalized bytes, when writing to GCS, then require an absent object and keep it private with PNG metadata")
    void upload() throws IOException {
      storage.write(key, new byte[] {1, 2});
      var info = ArgumentCaptor.forClass(BlobInfo.class);
      verify(client)
          .create(
              info.capture(), eq(new byte[] {1, 2}), eq(Storage.BlobTargetOption.doesNotExist()));
      assertThat(info.getValue().getBlobId()).isEqualTo(id);
      assertThat(info.getValue().getContentType()).isEqualTo("image/png");
      assertThat(info.getValue().getCacheControl()).isEqualTo("no-store");
      assertThat(info.getValue().getAcl()).isNull();
    }

    @Test
    @DisplayName(
        "Given GCS denies a write, when uploading, then propagate failure so the transaction rolls back")
    void failure() {
      var failure = new StorageException(403, "denied");
      when(client.create(
              any(BlobInfo.class), any(byte[].class), any(Storage.BlobTargetOption.class)))
          .thenThrow(failure);
      assertThatThrownBy(() -> storage.write(key, new byte[] {1})).isSameAs(failure);
    }
  }

  @Nested
  class Read {
    @Test
    @DisplayName(
        "Given a stored PNG, when reading, then return bytes from its configured bucket and key")
    void download() throws IOException {
      when(client.readAllBytes(id)).thenReturn(new byte[] {3});
      assertThat(storage.read(key)).containsExactly(3);
    }

    @Test
    @DisplayName("Given an unavailable object, when reading, then propagate the provider failure")
    void failure() {
      var failure = new StorageException(503, "offline");
      when(client.readAllBytes(id)).thenThrow(failure);
      assertThatThrownBy(() -> storage.read(key)).isSameAs(failure);
    }
  }

  @Nested
  class Delete {
    @Test
    @DisplayName(
        "Given an orphan then an absent object, when deleting twice, then both cleanup attempts succeed")
    void remove() throws IOException {
      when(client.delete(id)).thenReturn(true, false);
      storage.delete(key);
      storage.delete(key);
      verify(client, times(2)).delete(id);
    }

    @Test
    @DisplayName(
        "Given GCS deletion fails, when removing, then propagate failure for cleanup retry")
    void failure() {
      var failure = new StorageException(503, "offline");
      when(client.delete(id)).thenThrow(failure);
      assertThatThrownBy(() -> storage.delete(key)).isSameAs(failure);
    }
  }
}
