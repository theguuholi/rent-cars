package com.github.theguuholi.recommendations.rentals;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** HttpFS exposes HDFS over one HTTP endpoint without Hadoop client dependencies in Java 25. */
class HdfsArchiveClient {
  private final RestClient http;
  private final String user;

  HdfsArchiveClient(RestClient http, String user) {
    this.http = http;
    this.user = user;
  }

  Optional<String> read(String path) {
    try {
      return Optional.ofNullable(
              http.get()
                  .uri(builder ->
                      builder
                          .path("/webhdfs/v1" + path)
                          .queryParam("op", "OPEN")
                          .queryParam("user.name", user)
                          .build())
                  .retrieve()
                  .body(byte[].class))
          .map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    } catch (HttpClientErrorException.NotFound _) {
      return Optional.empty();
    }
  }

  void create(String path, String content) {
    // HttpFS's upload endpoint writes data directly; overwrite=false protects temporary names.
    http.put()
        .uri(builder ->
            builder
                .path("/webhdfs/v1" + path)
                .queryParam("op", "CREATE")
                .queryParam("user.name", user)
                .queryParam("data", true)
                .queryParam("overwrite", false)
                .build())
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(content.getBytes(StandardCharsets.UTF_8))
        .retrieve()
        .toBodilessEntity();
  }

  boolean rename(String source, String destination) {
    return http.put()
        .uri(builder ->
            builder
                .path("/webhdfs/v1" + source)
                .queryParam("op", "RENAME")
                .queryParam("user.name", user)
                .queryParam("destination", destination)
                .build())
        .retrieve()
        .body(OperationResult.class)
        .successful();
  }

  void delete(String path) {
    http.delete()
        .uri(builder ->
            builder
                .path("/webhdfs/v1" + path)
                .queryParam("op", "DELETE")
                .queryParam("user.name", user)
                .build())
        .retrieve()
        .toBodilessEntity();
  }

  private record OperationResult(@JsonProperty("boolean") boolean successful) {}
}
