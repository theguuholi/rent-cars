package com.github.theguuholi.recommendations.rentals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

class RentalEventsConsumerTest {
  static final UUID RENTAL = UUID.fromString("94f25dd5-b8ef-4b41-8d33-72c97cbac937");
  static final String JSON =
      """
      {"eventId":"b2a088d3-f66d-4f47-af43-746de9fc0375","eventType":"RentalOpened",
       "schemaVersion":1,"occurredAt":"2026-11-16T15:00:00Z",
       "rentalId":"94f25dd5-b8ef-4b41-8d33-72c97cbac937","payload":{"accountId":"private-account"}}
      """;
  final JsonMapper json = JsonMapper.builder().build();
  ValidatorFactory validation;
  RentalEventsConsumer consumer;
  Logger logger;
  ListAppender<ILoggingEvent> logs;

  @BeforeEach
  void setUp() {
    validation = Validation.buildDefaultValidatorFactory();
    consumer = new RentalEventsConsumer(json, validation.getValidator());
    logger = (Logger) LoggerFactory.getLogger(RentalEventsConsumer.class);
    logs = new ListAppender<>();
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(logs);
    logs.stop();
    validation.close();
  }

  ConsumerRecord<String, String> message(String key, String value) {
    return new ConsumerRecord<>("rentals.events", 2, 17L, key, value);
  }

  @Nested
  class Consume {
    @ParameterizedTest
    @ValueSource(strings = {"RentalOpened", "RentalReturned"})
    void givenSupportedEventWhenConsumedThenLogsItsIdentityAndKafkaPosition(String type) {
      consumer.consume(message(RENTAL.toString(), JSON.replace("RentalOpened", type)));
      assertThat(logs.list)
          .singleElement()
          .satisfies(log ->
              assertThat(log.getFormattedMessage())
                  .contains(
                      "b2a088d3-f66d-4f47-af43-746de9fc0375",
                      type,
                      RENTAL.toString(),
                      "partition=2",
                      "offset=17")
                  .doesNotContain("private-account", "payload"));
    }

    @Test
    void givenDuplicateEventWhenConsumedTwiceThenBothReceiptsAreObservableWithoutBusinessWrites() {
      var message = message(RENTAL.toString(), JSON);
      consumer.consume(message);
      consumer.consume(message);
      assertThat(logs.list).hasSize(2);
    }

    @Test
    void givenAdditionalFieldsWhenConsumedThenAcceptsCompatibleSchemaEvolution() {
      var value = json.readTree(JSON).deepCopy();
      ((tools.jackson.databind.node.ObjectNode) value).put("futureField", "ignored");
      consumer.consume(message(RENTAL.toString(), value.toString()));
      assertThat(logs.list).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {"eventId", "eventType", "schemaVersion", "occurredAt", "rentalId", "payload"})
    void givenMissingEnvelopeFieldWhenConsumedThenRejectsWithoutReceipt(String field) {
      var value = (tools.jackson.databind.node.ObjectNode) json.readTree(JSON);
      value.remove(field);
      var message = message(RENTAL.toString(), value.toString());
      assertThatThrownBy(() -> consumer.consume(message)).isInstanceOf(RuntimeException.class);
      assertThat(logs.list).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "[]", "", "{\"eventId\":\"invalid-uuid\"}"})
    void givenMalformedEnvelopeWhenConsumedThenRejectsWithoutReceipt(String value) {
      var message = message(RENTAL.toString(), value);
      assertThatThrownBy(() -> consumer.consume(message)).isInstanceOf(RuntimeException.class);
      assertThat(logs.list).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "2"})
    void givenUnsupportedVersionWhenConsumedThenRejectsWithoutReceipt(String version) {
      var message =
          message(
              RENTAL.toString(),
              JSON.replace("\"schemaVersion\":1", "\"schemaVersion\":" + version));
      assertThatThrownBy(() -> consumer.consume(message)).isInstanceOf(RuntimeException.class);
      assertThat(logs.list).isEmpty();
    }

    @Test
    void givenUnknownEventTypeWhenConsumedThenRejectsWithoutReceipt() {
      var message = message(RENTAL.toString(), JSON.replace("RentalOpened", "RentalDeleted"));
      assertThatThrownBy(() -> consumer.consume(message)).isInstanceOf(RuntimeException.class);
      assertThat(logs.list).isEmpty();
    }

    @Test
    void givenNonObjectPayloadWhenConsumedThenRejectsWithoutReceipt() {
      var value = (tools.jackson.databind.node.ObjectNode) json.readTree(JSON);
      value.put("payload", "not-an-object");
      var message = message(RENTAL.toString(), value.toString());
      assertThatThrownBy(() -> consumer.consume(message))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(logs.list).isEmpty();
    }

    @Test
    void givenMismatchedKeyWhenConsumedThenRejectsWithoutReceipt() {
      var message = message(UUID.randomUUID().toString(), JSON);
      assertThatThrownBy(() -> consumer.consume(message))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(logs.list).isEmpty();
    }

    @Test
    void givenMissingKeyWhenConsumedThenRejectsWithoutReceipt() {
      var message = message(null, JSON);
      assertThatThrownBy(() -> consumer.consume(message))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(logs.list).isEmpty();
    }

    @Test
    void givenTombstoneWhenConsumedThenRejectsWithoutReceipt() {
      var message = message(RENTAL.toString(), null);
      assertThatThrownBy(() -> consumer.consume(message)).isInstanceOf(RuntimeException.class);
      assertThat(logs.list).isEmpty();
    }
  }
}
