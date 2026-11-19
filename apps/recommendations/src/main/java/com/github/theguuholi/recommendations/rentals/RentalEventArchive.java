package com.github.theguuholi.recommendations.rentals;

import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

/** Publish complete immutable files; replay may repeat writes but cannot overwrite an event. */
class RentalEventArchive {
  private final HdfsArchiveClient hdfs;
  private final ObjectMapper json;
  private final String root;

  RentalEventArchive(HdfsArchiveClient hdfs, ObjectMapper json, String root) {
    this.hdfs = hdfs;
    this.json = json;
    this.root = root;
  }

  void store(UUID eventId, String payload) {
    String folder = root + "/" + eventId.toString().substring(0, 2);
    String destination = folder + "/" + eventId + ".json";
    var existing = hdfs.read(destination);
    if (existing.isPresent()) {
      checkIdentical(eventId, existing.orElseThrow(), payload);
      return;
    }

    String temporary = folder + "/." + eventId + "." + UUID.randomUUID() + ".tmp";
    try {
      hdfs.create(temporary, payload);
      if (!hdfs.rename(temporary, destination)) {
        String winner =
            hdfs.read(destination)
                .orElseThrow(
                    () -> new IllegalStateException("Could not publish archived event " + eventId));
        checkIdentical(eventId, winner, payload);
      }
    } finally {
      // Missing temporary files are normal after rename. Cleanup failures remain retryable.
      hdfs.delete(temporary);
    }
  }

  private void checkIdentical(UUID eventId, String existing, String payload) {
    if (!json.readTree(existing).equals(json.readTree(payload))) {
      throw new IllegalArgumentException("Conflicting archived event " + eventId);
    }
  }
}
