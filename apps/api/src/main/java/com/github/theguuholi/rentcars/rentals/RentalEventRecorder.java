package com.github.theguuholi.rentcars.rentals;

import com.github.theguuholi.rentcars.outbox.OutboxMessage;
import com.github.theguuholi.rentcars.outbox.OutboxRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
@Transactional(propagation = Propagation.MANDATORY)
class RentalEventRecorder {
  private final OutboxRepository messages;
  private final ObjectMapper json;

  RentalEventRecorder(OutboxRepository messages, ObjectMapper json) {
    this.messages = messages;
    this.json = json;
  }

  void opened(Rental rental, UUID accountId) {
    record("RentalOpened", rental, accountId, rental.startDate(), null, null);
  }

  void returned(Rental rental, UUID accountId, Instant returnedAt, long totalCents) {
    record("RentalReturned", rental, accountId, returnedAt, returnedAt, totalCents);
  }

  private void record(
      String type,
      Rental rental,
      UUID accountId,
      Instant occurredAt,
      Instant returnedAt,
      Long totalCents) {
    var event =
        new RentalEvent(
            UUID.randomUUID(),
            type,
            1,
            occurredAt,
            rental.id(),
            new RentalData(
                accountId,
                rental.carId(),
                rental.startDate(),
                rental.expectedReturnDate(),
                returnedAt,
                rental.dailyRateCents(),
                rental.fineAmountCents(),
                totalCents,
                "BRL"));
    messages.save(
        new OutboxMessage(
            event.eventId(), rental.id(), json.writeValueAsString(event), occurredAt));
  }

  record RentalEvent(
      UUID eventId,
      String eventType,
      int schemaVersion,
      Instant occurredAt,
      UUID rentalId,
      RentalData payload) {}

  record RentalData(
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
