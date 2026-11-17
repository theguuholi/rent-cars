package com.github.theguuholi.rentcars.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxPublisher {
  private static final Logger LOG = LoggerFactory.getLogger(OutboxPublisher.class);
  private final OutboxRepository messages;
  private final KafkaTemplate<String, String> kafka;
  private final Clock clock;
  private final MeterRegistry metrics;
  private final String topic;

  OutboxPublisher(
      OutboxRepository messages,
      KafkaTemplate<String, String> kafka,
      Clock clock,
      MeterRegistry metrics,
      @Value("${rent.messaging.topic}") String topic) {
    this.messages = messages;
    this.kafka = kafka;
    this.clock = clock;
    this.metrics = metrics;
    this.topic = topic;
    metrics.gauge("outbox.pending", messages, OutboxRepository::countByPublishedAtIsNull);
    metrics.gauge(
        "outbox.oldest.pending.seconds", messages, OutboxRepository::oldestPendingSeconds);
  }

  /**
   * One bounded send per transaction. A failed head blocks later events to preserve publication
   * order.
   */
  @Transactional
  @Observed(name = "outbox.publish")
  public boolean publishNext() {
    // 1. Only one publisher may process the Outbox during this transaction.
    if (!messages.tryPublisherLock()) {
      return false;
    }

    // 2. Keep publication order: do not skip the oldest message during its retry delay.
    var pending = messages.findFirstByPublishedAtIsNullOrderBySequenceAsc();
    if (pending.isEmpty()) {
      return false;
    }

    OutboxMessage message = pending.orElseThrow();
    if (!message.ready(clock.instant())) {
      return false;
    }

    // 3. Wait for Kafka confirmation before recording a successful publication.
    return publishAndRecordResult(message);
  }

  private boolean publishAndRecordResult(OutboxMessage message) {
    try {
      String rentalKey = message.rentalId().toString();
      var confirmation = kafka.send(topic, rentalKey, message.payload());
      confirmation.get(5, TimeUnit.SECONDS);

      message.published(clock.instant());
      metrics.counter("outbox.send", "result", "acknowledged").increment();
      return true;
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
      recordFailure(message);
      return false;
    } catch (ExecutionException | TimeoutException | RuntimeException _) {
      recordFailure(message);
      return false;
    }
  }

  private void recordFailure(OutboxMessage message) {
    message.failed(clock.instant());
    metrics.counter("outbox.send", "result", "failed").increment();
    LOG.warn("Kafka send failed; eventId={} remains pending", message.eventId());
  }
}
