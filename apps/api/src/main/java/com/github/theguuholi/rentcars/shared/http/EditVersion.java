package com.github.theguuholi.rentcars.shared.http;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Checks the form snapshot; JPA checks the same version again when flushing the write. */
public final class EditVersion {
  private EditVersion() {}

  public static void requireCurrent(Long expected, long current) {
    if (expected == null)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "expectedVersion is required for updates");
    if (expected != current) throw new OptimisticLockingFailureException("Stale form version");
  }
}
