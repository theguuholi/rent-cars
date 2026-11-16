package com.github.theguuholi.rentcars.outbox;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {
  // Transaction-scoped: automatically released on commit, rollback or connection loss.
  @Query(value = "select pg_try_advisory_xact_lock(728194001)", nativeQuery = true)
  boolean tryPublisherLock();

  Optional<OutboxMessage> findFirstByPublishedAtIsNullOrderBySequenceAsc();

  long countByPublishedAtIsNull();

  @Query(
      value =
          "select coalesce(extract(epoch from (current_timestamp - min(occurred_at))), 0) from outbox_messages where published_at is null",
      nativeQuery = true)
  double oldestPendingSeconds();
}
