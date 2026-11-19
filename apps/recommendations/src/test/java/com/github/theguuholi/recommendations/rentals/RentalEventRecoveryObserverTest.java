package com.github.theguuholi.recommendations.rentals;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class RentalEventRecoveryObserverTest {
  final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
  final ConsumerRecord<String, String> record =
      new ConsumerRecord<>("rentals.events", 2, 17, "key", "private-payload");
  final Exception failure = new IllegalStateException("private-error-details");

  @Nested
  class FailedDelivery {
    @Test
    void givenFailedAttemptWhenObservedThenCountsFailureWithoutLoggingPayload(
        CapturedOutput output) {
      new RentalEventRecoveryObserver(metrics).failedDelivery(record, failure, 2);
      assertThat(metrics.get("rental.consumer.failures").counter().count()).isEqualTo(1);
      assertThat(output.getAll())
          .contains("attempt=2", "partition=2", "offset=17")
          .doesNotContain("private-payload", "private-error-details");
    }
  }

  @Nested
  class Recovered {
    @Test
    void givenConfirmedDltWhenObservedThenCountsRecovery(CapturedOutput output) {
      new RentalEventRecoveryObserver(metrics).recovered(record, failure);
      assertThat(metrics.get("rental.consumer.dlt.published").counter().count()).isEqualTo(1);
      assertThat(output.getAll())
          .contains("DLT confirmed", "topic=rentals.events", "partition=2", "offset=17")
          .doesNotContain("private-payload");
    }
  }

  @Nested
  class RecoveryFailed {
    @Test
    void givenFailedDltWhenObservedThenCountsAndReportsPendingRecord(CapturedOutput output) {
      new RentalEventRecoveryObserver(metrics).recoveryFailed(record, failure, failure);
      assertThat(metrics.get("rental.consumer.dlt.failures").counter().count()).isEqualTo(1);
      assertThat(output.getAll())
          .contains("DLT failed; record remains pending", "partition=2", "offset=17")
          .doesNotContain("private-payload");
    }
  }
}
