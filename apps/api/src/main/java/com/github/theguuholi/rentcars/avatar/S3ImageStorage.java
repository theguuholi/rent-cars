package com.github.theguuholi.rentcars.avatar;

import java.util.UUID;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Private, immutable objects. Authentication and authorization remain at the API boundary. */
class S3ImageStorage implements ImageStorage {
  private final S3Client client;
  private final String bucket;

  S3ImageStorage(S3Client client, String bucket) {
    this.client = client;
    this.bucket = bucket;
  }

  public void write(UUID key, byte[] image) {
    client.putObject(
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(key + ".png")
            .contentType("image/png")
            .cacheControl("no-store")
            .ifNoneMatch("*")
            .build(),
        RequestBody.fromBytes(image));
  }

  public byte[] read(UUID key) {
    return client
        .getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key + ".png").build())
        .asByteArray();
  }

  public void delete(UUID key) {
    client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key + ".png").build());
  }
}
