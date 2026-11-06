package com.github.theguuholi.rentcars.shared.storage;

public interface ImageStorage {
  void store(String key, byte[] bytes, String contentType);

  byte[] load(String key);

  void delete(String key);
}
