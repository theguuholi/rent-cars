package com.github.theguuholi.rentcars.shared.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImageCleanup {
  private static final Logger log = LoggerFactory.getLogger(ImageCleanup.class);
  private final JdbcTemplate jdbc;
  private final ImageStorage storage;

  public ImageCleanup(JdbcTemplate jdbc, ImageStorage storage) {
    this.jdbc = jdbc;
    this.storage = storage;
  }

  /** Committed before upload so even a terminated process leaves durable cleanup intent. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void prepare(String key) {
    jdbc.update(
        "insert into image_cleanup_jobs(storage_key, available_at) values (?, CURRENT_TIMESTAMP + interval '1 hour')",
        key);
  }

  /** The upload transaction holds this lock until its metadata commits or rolls back. */
  public void lock(String key) {
    jdbc.queryForObject(
        "select storage_key from image_cleanup_jobs where storage_key = ? for update",
        String.class,
        key);
  }

  public void complete(String key) {
    jdbc.update("delete from image_cleanup_jobs where storage_key = ?", key);
  }

  @Scheduled(
      fixedDelayString = "${rent.storage.cleanup-delay-ms:60000}",
      initialDelayString = "${rent.storage.cleanup-delay-ms:60000}")
  @Transactional
  public void run() {
    var keys =
        jdbc.queryForList(
            """
        select storage_key from image_cleanup_jobs
        where available_at <= CURRENT_TIMESTAMP
        order by available_at limit 100 for update skip locked
        """,
            String.class);
    for (String key : keys) {
      try {
        storage.delete(key);
        complete(key);
      } catch (RuntimeException exception) {
        jdbc.update(
            "update image_cleanup_jobs set available_at = CURRENT_TIMESTAMP + interval '5 minutes' where storage_key = ?",
            key);
        log.warn("Image cleanup failed for {}; the job will be retried", key, exception);
      }
    }
  }
}
