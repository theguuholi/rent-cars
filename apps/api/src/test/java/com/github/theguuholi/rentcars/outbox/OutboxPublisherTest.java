package com.github.theguuholi.rentcars.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class OutboxPublisherTest {
  final OutboxRepository repository = mock();
  final KafkaTemplate<String, String> kafka = mock();
  final Instant now = Instant.parse("2026-11-16T12:00:00Z");
  final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
  final OutboxPublisher publisher =
      new OutboxPublisher(
          repository, kafka, Clock.fixed(now, ZoneOffset.UTC), metrics, "rentals.events");
  final OutboxMessage message = new OutboxMessage(UUID.randomUUID(), UUID.randomUUID(), "{}", now);

  void pending() {
    when(repository.tryPublisherLock()).thenReturn(true);
    when(repository.findFirstByPublishedAtIsNullOrderBySequenceAsc())
        .thenReturn(Optional.of(message));
  }

  @Nested
  class PublishNext {
    @Test
    void givenOtherPublisherWhenPollingThenDoesNotReadOrSend() {
      assertThat(publisher.publishNext()).isFalse();
      verify(repository, never()).findFirstByPublishedAtIsNullOrderBySequenceAsc();
      verifyNoInteractions(kafka);
    }

    @Test
    void givenEmptyOutboxWhenPollingThenDoesNotSend() {
      when(repository.tryPublisherLock()).thenReturn(true);
      assertThat(publisher.publishNext()).isFalse();
      verifyNoInteractions(kafka);
    }

    @Test
    void givenBackoffWhenPollingThenDoesNotSkipHeadOrSend() {
      pending();
      message.failed(now);
      assertThat(publisher.publishNext()).isFalse();
      verifyNoInteractions(kafka);
    }

    @Test
    void givenAcknowledgmentWhenSendingThenMarksPublishedAndRecordsMetrics() {
      pending();
      when(kafka.send(anyString(), anyString(), anyString()))
          .thenReturn(CompletableFuture.completedFuture(null));
      assertThat(publisher.publishNext()).isTrue();
      verify(kafka).send("rentals.events", message.rentalId().toString(), "{}");
      assertThat(message).extracting("publishedAt").isEqualTo(now);
      assertThat(metrics.get("outbox.send").tag("result", "acknowledged").counter().count())
          .isEqualTo(1);
      when(repository.countByPublishedAtIsNull()).thenReturn(3L);
      when(repository.oldestPendingSeconds()).thenReturn(8.0);
      assertThat(metrics.get("outbox.pending").gauge().value()).isEqualTo(3);
      assertThat(metrics.get("outbox.oldest.pending.seconds").gauge().value()).isEqualTo(8);
    }

    @Test
    void givenFailedFutureWhenSendingThenKeepsPendingAndBacksOff() {
      pending();
      when(kafka.send(anyString(), anyString(), anyString()))
          .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));
      assertFailure();
    }

    @Test
    void givenSynchronousFailureWhenSendingThenKeepsPendingAndBacksOff() {
      pending();
      when(kafka.send(anyString(), anyString(), anyString()))
          .thenThrow(new IllegalStateException("offline"));
      assertFailure();
    }

    @Test
    void givenTimeoutWhenSendingThenKeepsPendingForPossibleDuplicateDelivery() throws Exception {
      pending();
      CompletableFuture<SendResult<String, String>> future = mock();
      when(kafka.send(anyString(), anyString(), anyString())).thenReturn(future);
      when(future.get(5, TimeUnit.SECONDS)).thenThrow(new TimeoutException());
      assertFailure();
    }

    @Test
    void givenInterruptionWhenSendingThenRestoresInterruptAndKeepsPending() throws Exception {
      pending();
      CompletableFuture<SendResult<String, String>> future = mock();
      when(kafka.send(anyString(), anyString(), anyString())).thenReturn(future);
      when(future.get(5, TimeUnit.SECONDS)).thenThrow(new InterruptedException());
      try {
        assertFailure();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
      } finally {
        Thread.interrupted();
      }
    }

    private void assertFailure() {
      assertThat(publisher.publishNext()).isFalse();
      assertThat(message).extracting("publishedAt").isNull();
      assertThat(message.ready(now)).isFalse();
      assertThat(metrics.get("outbox.send").tag("result", "failed").counter().count()).isEqualTo(1);
    }
  }
}
