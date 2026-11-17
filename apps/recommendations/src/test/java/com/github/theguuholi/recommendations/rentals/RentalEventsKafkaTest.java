package com.github.theguuholi.recommendations.rentals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.theguuholi.recommendations.RecommendationsApplication;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ContainerProperties;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class RentalEventsKafkaTest {
  @Container static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.1");
  final String topic = "rentals-" + UUID.randomUUID();
  final String group = "recommendations-" + UUID.randomUUID();

  @BeforeEach
  void createTopic() throws Exception {
    try (var admin = admin()) {
      admin
          .createTopics(List.of(new NewTopic(topic, 1, (short) 1)))
          .all()
          .get(10, TimeUnit.SECONDS);
    }
  }

  ConfigurableApplicationContext start(String groupId) {
    return new SpringApplicationBuilder(RecommendationsApplication.class)
        .run(
            "--spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
            "--spring.kafka.consumer.group-id=" + groupId,
            "--rent.messaging.topic=" + topic,
            "--spring.main.banner-mode=off");
  }

  Admin admin() {
    return Admin.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()));
  }

  void send(String value) throws Exception {
    try (var producer =
        new KafkaProducer<String, String>(
            Map.of("bootstrap.servers", KAFKA.getBootstrapServers(), "acks", "all"),
            new StringSerializer(),
            new StringSerializer())) {
      producer
          .send(new ProducerRecord<>(topic, 0, RentalEventsConsumerTest.RENTAL.toString(), value))
          .get(10, TimeUnit.SECONDS);
    }
  }

  long committed(String groupId) throws Exception {
    try (var admin = admin()) {
      var offsets =
          admin
              .listConsumerGroupOffsets(groupId)
              .partitionsToOffsetAndMetadata()
              .get(10, TimeUnit.SECONDS);
      var offset = offsets.get(new TopicPartition(topic, 0));
      return offset == null ? 0 : offset.offset();
    }
  }

  void awaitCommit(String groupId, long expected) {
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(committed(groupId)).isEqualTo(expected));
  }

  long receipts(CapturedOutput output, String eventId) {
    return output
        .getOut()
        .lines()
        .filter(line -> line.contains("Rental event received: eventId=" + eventId))
        .count();
  }

  static final String FIRST_ID = "b2a088d3-f66d-4f47-af43-746de9fc0375";
  static final String SECOND_ID = "c2a088d3-f66d-4f47-af43-746de9fc0375";
  static final String FIRST = RentalEventsConsumerTest.JSON;
  static final String SECOND =
      FIRST.replace(FIRST_ID, SECOND_ID).replace("RentalOpened", "RentalReturned");

  @Nested
  class Consumption {
    @Test
    void givenTwoTypesAndDuplicateWhenConsumedThenCommitsEverySuccessfulReceipt(
        CapturedOutput output) throws Exception {
      send(FIRST);
      send(SECOND);
      send(FIRST);
      try (var app = start(group)) {
        var container =
            app.getBean(KafkaListenerEndpointRegistry.class).getListenerContainer("rental-events");
        assertThat(container).isNotNull();
        assertThat(container.getContainerProperties().getAckMode())
            .isEqualTo(ContainerProperties.AckMode.RECORD);
        awaitCommit(group, 3);
        assertThat(receipts(output, FIRST_ID)).isEqualTo(2);
        assertThat(receipts(output, SECOND_ID)).isEqualTo(1);
      }
    }

    @Test
    void givenCommittedOffsetWhenApplicationRestartsThenResumesWithoutReplayingHistory(
        CapturedOutput output) throws Exception {
      send(FIRST);
      try (var app = start(group)) {
        assertThat(app.isActive()).isTrue();
        awaitCommit(group, 1);
      }
      send(SECOND);
      try (var restarted = start(group)) {
        assertThat(restarted.isActive()).isTrue();
        awaitCommit(group, 2);
        assertThat(receipts(output, FIRST_ID)).isEqualTo(1);
        assertThat(receipts(output, SECOND_ID)).isEqualTo(1);
      }
    }

    @Test
    void givenIndependentGroupWhenStartingThenReadsItsOwnCopyOfRetainedEvents(CapturedOutput output)
        throws Exception {
      send(FIRST);
      try (var app = start(group)) {
        assertThat(app.isActive()).isTrue();
        awaitCommit(group, 1);
      }
      String independentGroup = group + "-independent";
      try (var app = start(independentGroup)) {
        assertThat(app.isActive()).isTrue();
        awaitCommit(independentGroup, 1);
        assertThat(committed(group)).isEqualTo(1);
        assertThat(receipts(output, FIRST_ID)).isEqualTo(2);
      }
    }

    @Test
    void givenInvalidRecordWhenConsumingThenStopsWithoutCommittingOrSkippingIt(
        CapturedOutput output) throws Exception {
      send(FIRST);
      try (var app = start(group)) {
        awaitCommit(group, 1);
        send("not-json");
        send(SECOND);
        var container =
            app.getBean(KafkaListenerEndpointRegistry.class).getListenerContainer("rental-events");
        assertThat(container).isNotNull();
        await().atMost(Duration.ofSeconds(30)).until(() -> !container.isRunning());
        assertThat(committed(group)).isEqualTo(1);
        assertThat(receipts(output, SECOND_ID)).isZero();
      }
      try (var restarted = start(group)) {
        var container =
            restarted
                .getBean(KafkaListenerEndpointRegistry.class)
                .getListenerContainer("rental-events");
        assertThat(container).isNotNull();
        await().atMost(Duration.ofSeconds(30)).until(() -> !container.isRunning());
        assertThat(committed(group)).isEqualTo(1);
        assertThat(receipts(output, SECOND_ID)).isZero();
      }
    }
  }
}
