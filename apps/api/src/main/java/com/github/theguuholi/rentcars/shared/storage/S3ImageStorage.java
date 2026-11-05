package com.github.theguuholi.rentcars.shared.storage;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

final class S3ImageStorage implements ImageStorage {
  private final S3Client client;
  private final String bucket;

  S3ImageStorage(S3Client client, String bucket) {
    this.client = client;
    this.bucket = bucket;
  }

  @Override
  public void store(String key, byte[] bytes, String contentType) {
    client.putObject(
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(contentType)
            .ifNoneMatch("*")
            .build(),
        RequestBody.fromBytes(bytes));
  }

  @Override
  public byte[] load(String key) {
    try {
      return client
          .getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build())
          .asByteArray();
    } catch (NoSuchKeyException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image file not found");
    }
  }

  @Override
  public void delete(String key) {
    client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
  }
}
