package com.github.theguuholi.recommendations.rentals;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Logs receipts only. At-least-once delivery may repeat a receipt; no business state is written.
 */
@Component
class RentalEventsConsumer {
  private static final Logger LOG = LoggerFactory.getLogger(RentalEventsConsumer.class);
  private final ObjectMapper json;
  private final Validator validator;

  RentalEventsConsumer(ObjectMapper json, Validator validator) {
    this.json = json;
    this.validator = validator;
  }

  @KafkaListener(id = "rental-events", idIsGroup = false, topics = "${rent.messaging.topic}")
  void consume(ConsumerRecord<String, String> message) {
    RentalEvent event = json.readValue(message.value(), RentalEvent.class);
    var violations = validator.validate(event);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
    if (!event.payload().isObject()) {
      throw new IllegalArgumentException("Rental event payload must be an object");
    }
    if (!event.rentalId().toString().equals(message.key())) {
      throw new IllegalArgumentException("Kafka key must match rentalId");
    }
    LOG.info(
        "Rental event received: eventId={} eventType={} rentalId={} partition={} offset={}",
        event.eventId(),
        event.eventType(),
        event.rentalId(),
        message.partition(),
        message.offset());
  }
}
