package com.github.theguuholi.rentcars.avatar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "rent.storage.provider", havingValue = "local", matchIfMissing = true)
class LocalImageStorage implements ImageStorage {
  private final Path root;

  LocalImageStorage(@Value("${rent.storage.directory:.rent-cars/uploads}") Path root) {
    this.root = root;
  }

  private Path path(UUID key) {
    return root.resolve(key + ".png");
  }

  public void write(UUID key, byte[] image) throws IOException {
    Files.createDirectories(root);
    Files.write(path(key), image, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
  }

  public byte[] read(UUID key) throws IOException {
    return Files.readAllBytes(path(key));
  }

  public void delete(UUID key) throws IOException {
    Files.deleteIfExists(path(key));
  }
}
