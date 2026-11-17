package com.github.theguuholi.rentcars.rentals;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.time.Instant;
import java.util.UUID;

/** Immutable integration snapshot; constructing it does not publish or persist anything. */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
final class RentalEvent {
  private final UUID eventId = UUID.randomUUID();
  private final String eventType;
  private final int schemaVersion = 1;
  private final Instant occurredAt;
  private final UUID rentalId;
  private final RentalData payload;

  RentalEvent(Rental rental, UUID accountId) {
    this("RentalOpened", rental, accountId, rental.startDate(), null, null);
  }

  RentalEvent(Rental rental, UUID accountId, Instant returnedAt, long totalCents) {
    this("RentalReturned", rental, accountId, returnedAt, returnedAt, totalCents);
  }

  private RentalEvent(
      String type,
      Rental rental,
      UUID accountId,
      Instant occurredAt,
      Instant endDate,
      Long totalCents) {
    this.eventType = type;
    this.occurredAt = occurredAt;
    this.rentalId = rental.id();
    this.payload =
        new RentalData(
            accountId,
            rental.carId(),
            rental.startDate(),
            rental.expectedReturnDate(),
            endDate,
            rental.dailyRateCents(),
            rental.fineAmountCents(),
            totalCents,
            "BRL");
  }

  UUID eventId() {
    return eventId;
  }

  UUID rentalId() {
    return rentalId;
  }

  Instant occurredAt() {
    return occurredAt;
  }

  private record RentalData(
      UUID accountId,
      UUID carId,
      Instant startDate,
      Instant expectedReturnDate,
      Instant endDate,
      int dailyRateCents,
      int fineAmountCents,
      Long totalCents,
      String currency) {}
}
