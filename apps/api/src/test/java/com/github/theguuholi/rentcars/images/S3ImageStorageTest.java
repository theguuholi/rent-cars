package com.github.theguuholi.rentcars.images;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

class S3ImageStorageTest {
  final S3Client client = mock(S3Client.class);
  final UUID key = UUID.randomUUID();
  final ImageStorage storage = new S3ImageStorage(client, "private-avatars");

  @Nested
  class Write {
    @Test
    @DisplayName(
        "Given normalized bytes, when writing to S3, then use the private bucket, immutable PNG key and original bytes")
    void upload() throws IOException {
      storage.write(key, new byte[] {1, 2});
      var request = ArgumentCaptor.forClass(PutObjectRequest.class);
      var body = ArgumentCaptor.forClass(RequestBody.class);
      verify(client).putObject(request.capture(), body.capture());
      assertThat(request.getValue().bucket()).isEqualTo("private-avatars");
      assertThat(request.getValue().key()).isEqualTo(key + ".png");
      assertThat(request.getValue().contentType()).isEqualTo("image/png");
      assertThat(request.getValue().cacheControl()).isEqualTo("no-store");
      assertThat(request.getValue().ifNoneMatch()).isEqualTo("*");
      assertThat(request.getValue().acl()).isNull();
      try (var stream = body.getValue().contentStreamProvider().newStream()) {
        assertThat(stream.readAllBytes()).containsExactly(1, 2);
      }
    }

    @Test
    @DisplayName(
        "Given S3 denies a write, when uploading, then propagate failure so the transaction rolls back")
    void failure() {
      var failure = S3Exception.builder().statusCode(403).build();
      when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
          .thenThrow(failure);
      assertThatThrownBy(() -> storage.write(key, new byte[] {1})).isSameAs(failure);
    }
  }

  @Nested
  class Read {
    @Test
    @DisplayName(
        "Given a stored PNG, when reading, then return its bytes from the configured bucket and key")
    void download() throws IOException {
      var request = GetObjectRequest.builder().bucket("private-avatars").key(key + ".png").build();
      when(client.getObjectAsBytes(request))
          .thenReturn(
              ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] {3}));
      assertThat(storage.read(key)).containsExactly(3);
    }

    @Test
    @DisplayName(
        "Given S3 cannot read the object, when reading, then propagate the provider failure")
    void failure() {
      var failure = S3Exception.builder().statusCode(503).build();
      when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(failure);
      assertThatThrownBy(() -> storage.read(key)).isSameAs(failure);
    }
  }

  @Nested
  class Delete {
    @Test
    @DisplayName(
        "Given an orphan or already absent object, when deleting twice, then issue idempotent deletes for the exact key")
    void remove() throws IOException {
      storage.delete(key);
      storage.delete(key);
      verify(client, times(2))
          .deleteObject(
              DeleteObjectRequest.builder().bucket("private-avatars").key(key + ".png").build());
    }

    @Test
    @DisplayName("Given S3 deletion fails, when removing, then propagate failure for cleanup retry")
    void failure() {
      var failure = S3Exception.builder().statusCode(503).build();
      when(client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(failure);
      assertThatThrownBy(() -> storage.delete(key)).isSameAs(failure);
    }
  }
}
