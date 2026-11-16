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
    if (!messages.tryPublisherLock()) return false;
    var pending = messages.findFirstByPublishedAtIsNullOrderBySequenceAsc();
    if (pending.isEmpty()) return false;
    var message = pending.orElseThrow();
    if (!message.ready(clock.instant())) return false;
    try {
      kafka.send(topic, message.rentalId().toString(), message.payload()).get(5, TimeUnit.SECONDS);
      message.published(clock.instant());
      metrics.counter("outbox.send", "result", "acknowledged").increment();
      return true;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      recordFailure(message);
      return false;
    } catch (ExecutionException | TimeoutException | RuntimeException exception) {
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
