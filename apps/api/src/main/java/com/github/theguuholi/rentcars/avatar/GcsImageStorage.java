package com.github.theguuholi.rentcars.avatar;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import java.util.UUID;

/** Uses application default credentials; never publishes an object or stores a signed URL. */
class GcsImageStorage implements ImageStorage {
  private final Storage client;
  private final String bucket;

  GcsImageStorage(Storage client, String bucket) {
    this.client = client;
    this.bucket = bucket;
  }

  public void write(UUID key, byte[] image) {
    client.create(
        BlobInfo.newBuilder(bucket, key + ".png")
            .setContentType("image/png")
            .setCacheControl("no-store")
            .build(),
        image,
        Storage.BlobTargetOption.doesNotExist());
  }

  public byte[] read(UUID key) {
    return client.readAllBytes(BlobId.of(bucket, key + ".png"));
  }

  public void delete(UUID key) {
    client.delete(BlobId.of(bucket, key + ".png"));
  }
}
