package com.github.theguuholi.recommendations.rentals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import com.github.theguuholi.recommendations.RecommendationsApplication;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.KafkaHeaders;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class RentalEventsKafkaTest {
  @Container
  static final KafkaContainer KAFKA =
      new KafkaContainer("apache/kafka:4.1.1").withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

  final String topic = "rentals-" + UUID.randomUUID();
  final String group = "recommendations-" + UUID.randomUUID();

  @BeforeEach
  void createTopic() throws Exception {
    try (var admin = admin()) {
      admin
          .createTopics(
              List.of(
                  new NewTopic(topic, 1, (short) 1), new NewTopic(topic + ".DLT", 1, (short) 1)))
          .all()
          .get(10, TimeUnit.SECONDS);
    }
  }

  ConfigurableApplicationContext start(String groupId) {
    return start(groupId, new AtomicInteger(), 0);
  }

  ConfigurableApplicationContext start(String groupId, AtomicInteger attempts, int failures) {
    return new SpringApplicationBuilder(RecommendationsApplication.class)
        .initializers(context ->
            context
                .getBeanFactory()
                .addBeanPostProcessor(
                    new BeanPostProcessor() {
                      @Override
                      public Object postProcessAfterInitialization(Object bean, String name) {
                        if (bean instanceof RentalEventsConsumer listener) {
                          var instrumented = spy(listener);
                          doAnswer(
                                  invocation -> {
                                    ConsumerRecord<String, String> record =
                                        invocation.getArgument(0);
                                    if (FIRST.equals(record.value())
                                        && attempts.incrementAndGet() <= failures) {
                                      throw new IllegalStateException(
                                          "Temporary downstream failure");
                                    }
                                    return invocation.callRealMethod();
                                  })
                              .when(instrumented)
                              .consume(any());
                          return instrumented;
                        }
                        return bean;
                      }
                    }))
        .run(
            "--spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
            "--spring.kafka.consumer.group-id=" + groupId,
            "--rent.messaging.topic=" + topic,
            "--rent.messaging.dead-letter-topic=" + topic + ".DLT",
            "--rent.messaging.retry.initial-interval-ms=10",
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
          .send(
              new ProducerRecord<>(
                  topic,
                  0,
                  RentalEventsConsumerTest.RENTAL.toString(),
                  value,
                  List.of(
                      new org.apache.kafka.common.header.internals.RecordHeader(
                          "traceparent", "study-trace".getBytes(StandardCharsets.UTF_8)))))
          .get(10, TimeUnit.SECONDS);
    }
  }

  ConsumerRecord<String, String> deadLetter() {
    try (var consumer =
        new KafkaConsumer<String, String>(
            Map.of("bootstrap.servers", KAFKA.getBootstrapServers(), "enable.auto.commit", false),
            new StringDeserializer(),
            new StringDeserializer())) {
      var partition = new TopicPartition(topic + ".DLT", 0);
      consumer.assign(List.of(partition));
      consumer.seekToBeginning(List.of(partition));
      var records = new java.util.ArrayList<ConsumerRecord<String, String>>();
      await()
          .atMost(Duration.ofSeconds(15))
          .until(() -> {
            consumer.poll(Duration.ofMillis(100)).forEach(records::add);
            return !records.isEmpty();
          });
      assertThat(records).hasSize(1);
      return records.getFirst();
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
    void givenInvalidRecordWhenConsumingThenQuarantinesAndContinues(CapturedOutput output)
        throws Exception {
      send("not-json");
      send(SECOND);
      try (var app = start(group)) {
        assertThat(app.isActive()).isTrue();
        awaitCommit(group, 2);
        assertThat(receipts(output, SECOND_ID)).isEqualTo(1);
        var failed = deadLetter();
        assertThat(failed.key()).isEqualTo(RentalEventsConsumerTest.RENTAL.toString());
        assertThat(failed.value()).isEqualTo("not-json");
        assertThat(
                new String(
                    failed.headers().lastHeader("traceparent").value(), StandardCharsets.UTF_8))
            .isEqualTo("study-trace");
        assertThat(
                new String(
                    failed.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC).value(),
                    StandardCharsets.UTF_8))
            .isEqualTo(topic);
        assertThat(
                ByteBuffer.wrap(
                        failed.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_PARTITION).value())
                    .getInt())
            .isZero();
        assertThat(
                ByteBuffer.wrap(
                        failed.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET).value())
                    .getLong())
            .isZero();
        assertThat(failed.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN)).isNotNull();
      }
      try (var restarted = start(group)) {
        assertThat(restarted.isActive()).isTrue();
        awaitCommit(group, 2);
        assertThat(receipts(output, SECOND_ID)).isEqualTo(1);
      }
    }

    @Test
    void givenTemporaryFailureWhenRetrySucceedsThenCommitsWithoutDeadLetter(CapturedOutput output)
        throws Exception {
      send(FIRST);
      var attempts = new AtomicInteger();
      try (var app = start(group, attempts, 2)) {
        assertThat(app.isActive()).isTrue();
        awaitCommit(group, 1);
        assertThat(attempts).hasValue(3);
        assertThat(receipts(output, FIRST_ID)).isEqualTo(1);
        try (var admin = admin()) {
          var ends =
              admin
                  .listOffsets(
                      Map.of(
                          new TopicPartition(topic + ".DLT", 0),
                          org.apache.kafka.clients.admin.OffsetSpec.latest()))
                  .all()
                  .get(10, TimeUnit.SECONDS);
          assertThat(ends.get(new TopicPartition(topic + ".DLT", 0)).offset()).isZero();
        }
      }
    }

    @Test
    void givenPersistentFailureWhenRetriesExhaustThenPreservesEventAndContinues(
        CapturedOutput output) throws Exception {
      send(FIRST);
      send(SECOND);
      var attempts = new AtomicInteger();
      try (var app = start(group, attempts, Integer.MAX_VALUE)) {
        assertThat(app.isActive()).isTrue();
        awaitCommit(group, 2);
        assertThat(attempts).hasValue(4);
        assertThat(app.getBean(io.micrometer.jmx.JmxMeterRegistry.class)).isNotNull();
        var metrics = app.getBean(io.micrometer.core.instrument.MeterRegistry.class);
        assertThat(metrics.get("rental.consumer.failures").counter().count()).isEqualTo(4);
        assertThat(metrics.get("rental.consumer.dlt.published").counter().count()).isEqualTo(1);
        assertThat(deadLetter().value()).isEqualTo(FIRST);
        assertThat(receipts(output, FIRST_ID)).isZero();
        assertThat(receipts(output, SECOND_ID)).isEqualTo(1);
      }
    }

    @Test
    void givenUnavailableDltWhenPublicationFailsThenKeepsOffsetUntilDltReturns(
        CapturedOutput output) throws Exception {
      try (var admin = admin()) {
        admin.deleteTopics(List.of(topic + ".DLT")).all().get(10, TimeUnit.SECONDS);
      }
      send("not-json");
      send(SECOND);
      try (var app = start(group)) {
        assertThat(app.isActive()).isTrue();
        await()
            .atMost(Duration.ofSeconds(45))
            .until(() -> output.getAll().contains("Dead-letter publication to"));
        assertThat(committed(group)).isZero();
        await()
            .atMost(Duration.ofSeconds(10))
            .untilAsserted(() ->
                assertThat(
                        app.getBean(io.micrometer.core.instrument.MeterRegistry.class)
                            .get("rental.consumer.dlt.failures")
                            .counter()
                            .count())
                    .isGreaterThanOrEqualTo(1));
        assertThat(receipts(output, SECOND_ID)).isZero();
        try (var admin = admin()) {
          admin
              .createTopics(List.of(new NewTopic(topic + ".DLT", 1, (short) 1)))
              .all()
              .get(10, TimeUnit.SECONDS);
        }
        awaitCommit(group, 2);
        assertThat(deadLetter().value()).isEqualTo("not-json");
        assertThat(receipts(output, SECOND_ID)).isEqualTo(1);
      }
    }
  }
}
