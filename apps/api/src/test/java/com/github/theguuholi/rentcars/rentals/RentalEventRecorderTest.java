package com.github.theguuholi.rentcars.rentals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.github.theguuholi.rentcars.outbox.OutboxMessage;
import com.github.theguuholi.rentcars.outbox.OutboxRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class RentalEventRecorderTest {
  final OutboxRepository messages = mock();
  final JsonMapper json = spy(JsonMapper.builder().build());
  final RentalEventRecorder recorder = new RentalEventRecorder(messages, json);
  final Rental rental = mock();
  final UUID accountId = UUID.randomUUID();
  final UUID rentalId = UUID.randomUUID();
  final Instant now = Instant.parse("2026-11-17T12:00:00Z");

  @Nested
  class Opened {
    @Test
    void givenRentalWhenRecordingOpeningThenSavesMatchingEnvelopeAndOutboxMetadata() {
      when(rental.id()).thenReturn(rentalId);
      when(rental.startDate()).thenReturn(now);
      recorder.opened(rental, accountId);
      assertSaved("RentalOpened", now);
    }

    @Test
    void givenSerializationFailureWhenRecordingThenDoesNotSaveAnIncompleteMessage() {
      doThrow(new IllegalStateException("Cannot serialize")).when(json).writeValueAsString(any());
      assertThatThrownBy(() -> recorder.opened(rental, accountId))
          .isInstanceOf(IllegalStateException.class);
      verifyNoInteractions(messages);
    }
  }

  @Nested
  class Returned {
    @Test
    void givenReturnWhenRecordingThenSavesMatchingEnvelopeAndOutboxMetadata() {
      when(rental.id()).thenReturn(rentalId);
      recorder.returned(rental, accountId, now, 25000);
      assertSaved("RentalReturned", now);
    }
  }

  private void assertSaved(String type, Instant occurredAt) {
    var captor = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(messages).save(captor.capture());
    var message = captor.getValue();
    var payload =
        json.readTree(
            (String)
                org.springframework.test.util.ReflectionTestUtils.getField(message, "payload"));
    assertThat(payload.get("eventType").asText()).isEqualTo(type);
    assertThat(message)
        .extracting("eventId", "rentalId", "occurredAt", "nextAttemptAt", "publishedAt")
        .containsExactly(
            UUID.fromString(payload.get("eventId").asText()),
            rentalId,
            occurredAt,
            occurredAt,
            null);
    assertThat(payload.get("rentalId").asText()).isEqualTo(rentalId.toString());
    assertThat(Instant.parse(payload.get("occurredAt").asText())).isEqualTo(occurredAt);
  }
}
