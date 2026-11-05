package com.github.theguuholi.rentcars.images;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reclaims unreferenced avatars and car photos, including interrupted uploads. */
@Service
public class ImageCleanup {
  private final ImageObjectRepository objects;
  private final ImageStorage storage;

  public ImageCleanup(ImageObjectRepository objects, ImageStorage storage) {
    this.objects = objects;
    this.storage = storage;
  }

  @Scheduled(fixedDelayString = "${rent.storage.cleanup-delay:PT10M}")
  @Transactional
  public void clean() {
    for (var object : objects.lockOrphans()) {
      var key = object.id();
      // Recheck after acquiring the lock in case an in-flight writer committed while selecting.
      if (objects.deleteUnreferenced(key) == 1) {
        try {
          storage.delete(key);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }
  }
}
