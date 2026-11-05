package com.github.theguuholi.rentcars.avatar;

import java.io.IOException;
import java.util.UUID;

interface ImageStorage {
  void write(UUID key, byte[] image) throws IOException;

  byte[] read(UUID key) throws IOException;

  void delete(UUID key) throws IOException;
}
