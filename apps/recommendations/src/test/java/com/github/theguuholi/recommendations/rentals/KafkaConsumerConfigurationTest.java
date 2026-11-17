package com.github.theguuholi.recommendations.rentals;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler;

class KafkaConsumerConfigurationTest {
  @Nested
  class RentalEventsErrorHandler {
    @Test
    void givenConsumerFailureWhenChoosingRecoveryThenStopsInsteadOfDiscarding() {
      var handler = new KafkaConsumerConfiguration().rentalEventsErrorHandler();
      assertThat(handler).isInstanceOf(CommonContainerStoppingErrorHandler.class);
    }
  }
}
