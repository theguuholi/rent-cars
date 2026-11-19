package com.github.theguuholi.recommendations.rentals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.util.backoff.BackOffExecution;
import tools.jackson.databind.json.JsonMapper;

class KafkaConsumerConfigurationTest {
  final KafkaConsumerConfiguration configuration = new KafkaConsumerConfiguration();

  @Nested
  class RentalRetryBackOff {
    @Test
    void givenDefaultPolicyWhenFailuresRepeatThenWaitsOneTwoFourSecondsAndStops() {
      var retry = configuration.rentalRetryBackOff(3, 1000).start();
      assertThat(retry.nextBackOff()).isEqualTo(1000);
      assertThat(retry.nextBackOff()).isEqualTo(2000);
      assertThat(retry.nextBackOff()).isEqualTo(4000);
      assertThat(retry.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }

    @Test
    void givenCustomPolicyWhenFailuresRepeatThenUsesConfiguredIntervalAndLimit() {
      var retry = configuration.rentalRetryBackOff(1, 25).start();
      assertThat(retry.nextBackOff()).isEqualTo(25);
      assertThat(retry.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }
  }

  @Nested
  class RentalEventsErrorHandler {
    @Test
    void givenInvalidMessagesWhenHandlingThenRecoversWithoutRetry() {
      var recoverer = mock(DeadLetterPublishingRecoverer.class);
      var handler =
          configuration.rentalEventsErrorHandler(
              recoverer,
              configuration.rentalRetryBackOff(3, 1),
              new RentalEventRecoveryObserver(
                  new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
      var consumer = mock(Consumer.class);
      var container = mock(MessageListenerContainer.class);
      var record = new ConsumerRecord<String, String>("rentals.events", 2, 17, "key", "value");
      Exception malformed;
      try {
        JsonMapper.builder().build().readTree("not-json");
        throw new AssertionError("Expected malformed JSON");
      } catch (tools.jackson.core.JacksonException exception) {
        malformed = exception;
      }
      for (Exception failure :
          new Exception[] {
            malformed,
            new ConstraintViolationException(Set.of()),
            new IllegalArgumentException("key mismatch")
          }) {
        assertThat(handler.handleOne(failure, record, consumer, container)).isTrue();
        verify(recoverer).accept(record, consumer, failure);
      }
      assertThat(handler.isAckAfterHandle()).isTrue();
    }
  }

  @Nested
  class RentalDeadLetterRecoverer {
    @Test
    @SuppressWarnings("unchecked")
    void givenFailedDltSendWhenRecoveringThenThrowsInsteadOfAcknowledging() {
      KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
      ProducerFactory<String, String> factory = mock(ProducerFactory.class);
      when(kafka.getProducerFactory()).thenReturn(factory);
      when(factory.getConfigurationProperties()).thenReturn(Map.of());
      when(kafka.send(any(ProducerRecord.class)))
          .thenReturn(
              CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
      var recoverer = configuration.rentalDeadLetterRecoverer(kafka, "recommendations.DLT");
      var record = new ConsumerRecord<String, String>("rentals.events", 2, 17, "key", "value");
      var failure = new IllegalStateException("processing failed");
      assertThatThrownBy(() -> recoverer.accept(record, failure))
          .isInstanceOf(org.springframework.kafka.KafkaException.class);
      var sent = org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
      verify(kafka).send(sent.capture());
      assertThat(sent.getValue().topic()).isEqualTo("recommendations.DLT");
      assertThat(sent.getValue().partition()).isEqualTo(2);
      assertThat(sent.getValue().key()).isEqualTo("key");
      assertThat(sent.getValue().value()).isEqualTo("value");
    }
  }
}
