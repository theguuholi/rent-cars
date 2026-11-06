package com.github.theguuholi.rentcars.shared.storage;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("rent.storage")
public record StorageProperties(@DefaultValue S3 s3) {
  public record S3(
      @DefaultValue("") String bucket,
      @DefaultValue("") String region,
      URI endpoint,
      @DefaultValue("false") boolean pathStyle) {}
}
