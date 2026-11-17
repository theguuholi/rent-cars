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
    saveEvent(new RentalEvent(rental, accountId));
  }

  void returned(Rental rental, UUID accountId, Instant returnedAt, long totalCents) {
    saveEvent(new RentalEvent(rental, accountId, returnedAt, totalCents));
  }

  private void saveEvent(RentalEvent event) {
    messages.save(
        new OutboxMessage(
            event.eventId(), event.rentalId(), json.writeValueAsString(event), event.occurredAt()));
  }
}
