package com.github.theguuholi.rentcars.rentals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RentalEventTest {
  final JsonMapper json = JsonMapper.builder().build();
  final Rental rental = mock();
  final UUID rentalId = UUID.randomUUID(), accountId = UUID.randomUUID(), carId = UUID.randomUUID();
  final Instant start = Instant.parse("2026-11-17T12:00:00Z");

  @BeforeEach
  void rentalSnapshot() {
    when(rental.id()).thenReturn(rentalId);
    when(rental.carId()).thenReturn(carId);
    when(rental.startDate()).thenReturn(start);
    when(rental.expectedReturnDate()).thenReturn(start.plusSeconds(86400));
    when(rental.dailyRateCents()).thenReturn(10000);
    when(rental.fineAmountCents()).thenReturn(3000);
  }

  @Nested
  class Opening {
    @Test
    void givenRentalWhenConstructingOpeningThenPreservesTheCompleteVersionOneJsonContract() {
      var event = new RentalEvent(rental, accountId);
      assertContract(event, "RentalOpened", start, "null", "null");
    }

    @Test
    void givenCreatedSnapshotWhenSourceChangesThenPayloadAndIdentityRemainStable() {
      var event = new RentalEvent(rental, accountId);
      String original = json.writeValueAsString(event);
      when(rental.dailyRateCents()).thenReturn(99999);
      when(rental.expectedReturnDate()).thenReturn(start.plusSeconds(172800));
      assertThat(json.writeValueAsString(event)).isEqualTo(original);
      assertThat(new RentalEvent(rental, accountId).eventId()).isNotEqualTo(event.eventId());
    }
  }

  @Nested
  class Return {
    @Test
    void givenReturnedRentalWhenConstructingThenPreservesFinalChargeAndReturnInstant() {
      Instant returnedAt = start.plusSeconds(172800);
      var event = new RentalEvent(rental, accountId, returnedAt, 23000);
      assertContract(event, "RentalReturned", returnedAt, "\"" + returnedAt + "\"", "23000");
    }
  }

  private void assertContract(
      RentalEvent event, String type, Instant occurredAt, String endDate, String totalCents) {
    assertThat(event.eventId()).isNotNull();
    assertThat(event.rentalId()).isEqualTo(rentalId);
    assertThat(event.occurredAt()).isEqualTo(occurredAt);
    var expected =
        json.readTree(
            """
        {"eventId":"%s","eventType":"%s","schemaVersion":1,"occurredAt":"%s","rentalId":"%s",
         "payload":{"accountId":"%s","carId":"%s","startDate":"%s","expectedReturnDate":"%s",
         "endDate":%s,"dailyRateCents":10000,"fineAmountCents":3000,"totalCents":%s,"currency":"BRL"}}
        """
                .formatted(
                    event.eventId(),
                    type,
                    occurredAt,
                    rentalId,
                    accountId,
                    carId,
                    start,
                    start.plusSeconds(86400),
                    endDate,
                    totalCents));
    assertThat(json.readTree(json.writeValueAsString(event))).isEqualTo(expected);
  }
}
