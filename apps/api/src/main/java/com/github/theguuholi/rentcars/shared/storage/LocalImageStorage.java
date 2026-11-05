package com.github.theguuholi.rentcars.shared.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class LocalImageStorage implements ImageStorage {
  private final Path root;

  LocalImageStorage(Path directory) {
    root = directory.toAbsolutePath().normalize();
  }

  private Path resolve(String key) {
    Path path = root.resolve(key).normalize();
    if (key.isBlank() || Path.of(key).isAbsolute() || !path.startsWith(root) || path.equals(root)) {
      throw new IllegalArgumentException("Invalid storage key");
    }
    for (Path part = path;
        part != null && !part.equals(root.getParent());
        part = part.getParent()) {
      if (Files.isSymbolicLink(part))
        throw new IllegalArgumentException("Storage paths cannot contain symbolic links");
    }
    return path;
  }

  @Override
  public void store(String key, byte[] bytes, String contentType) {
    Path path = resolve(key);
    try {
      Files.createDirectories(path.getParent());
      Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  @Override
  public byte[] load(String key) {
    try {
      return Files.readAllBytes(resolve(key));
    } catch (NoSuchFileException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image file not found");
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  @Override
  public void delete(String key) {
    try {
      Files.deleteIfExists(resolve(key));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
