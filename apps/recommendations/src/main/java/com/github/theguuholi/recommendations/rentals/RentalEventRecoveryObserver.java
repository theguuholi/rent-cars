package com.github.theguuholi.recommendations.rentals;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.stereotype.Component;

/** Recovery signals contain Kafka coordinates, never the event payload or personal data. */
@Component
class RentalEventRecoveryObserver implements RetryListener {
  private static final Logger LOG = LoggerFactory.getLogger(RentalEventRecoveryObserver.class);
  private final MeterRegistry metrics;

  RentalEventRecoveryObserver(MeterRegistry metrics) {
    this.metrics = metrics;
  }

  @Override
  public void failedDelivery(ConsumerRecord<?, ?> record, Exception exception, int attempt) {
    metrics.counter("rental.consumer.failures").increment();
    LOG.warn(
        "Rental delivery failed: topic={} partition={} offset={} attempt={}",
        record.topic(),
        record.partition(),
        record.offset(),
        attempt);
  }

  @Override
  public void recovered(ConsumerRecord<?, ?> record, Exception exception) {
    metrics.counter("rental.consumer.dlt.published").increment();
    LOG.warn(
        "DLT confirmed: topic={} partition={} offset={}",
        record.topic(),
        record.partition(),
        record.offset());
  }

  @Override
  public void recoveryFailed(ConsumerRecord<?, ?> record, Exception original, Exception failure) {
    metrics.counter("rental.consumer.dlt.failures").increment();
    LOG.error(
        "DLT failed; record remains pending: topic={} partition={} offset={}",
        record.topic(),
        record.partition(),
        record.offset());
  }
}
