package com.github.theguuholi.rentcars.shared.storage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("rent.storage")
public record StorageProperties(
    @DefaultValue("local") @NotNull Provider provider,
    @DefaultValue @Valid Local local,
    @DefaultValue @Valid S3 s3) {
  public enum Provider {
    local,
    s3
  }

  public record Local(@DefaultValue("./uploads") @NotNull Path directory) {}

  public record S3(@DefaultValue("") String bucket, @DefaultValue("") String region) {}
}
