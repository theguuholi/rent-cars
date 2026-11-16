package com.github.theguuholi.rentcars.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.theguuholi.rentcars.TestcontainersConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers
class OutboxRepositoryTest {
  @Container static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.1");

  @DynamicPropertySource
  static void kafka(DynamicPropertyRegistry properties) {
    properties.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
  }

  @Autowired OutboxRepository repository;
  @Autowired OutboxPublisher publisher;
  @Autowired TransactionTemplate tx;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void reset() throws Exception {
    repository.deleteAll();
    try (var admin = Admin.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
      if (!admin.listTopics().names().get(10, TimeUnit.SECONDS).contains("rentals.events")) {
        admin
            .createTopics(List.of(new NewTopic("rentals.events", 3, (short) 1)))
            .all()
            .get(10, TimeUnit.SECONDS);
      }
    }
  }

  OutboxMessage pending(UUID rentalId) {
    UUID eventId = UUID.randomUUID();
    return repository.saveAndFlush(
        new OutboxMessage(
            eventId, rentalId, "{\"eventId\":\"" + eventId + "\"}", Instant.now().minusSeconds(1)));
  }

  List<ConsumerRecord<String, String>> read(UUID rentalId, int count) {
    try (var consumer =
        new KafkaConsumer<String, String>(
            Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false),
            new StringDeserializer(),
            new StringDeserializer())) {
      consumer.subscribe(List.of("rentals.events"));
      var rows = new ArrayList<ConsumerRecord<String, String>>();
      long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
      while (rows.size() < count && System.nanoTime() < deadline) {
        for (var record : consumer.poll(Duration.ofMillis(200))) {
          if (rentalId.toString().equals(record.key())) rows.add(record);
        }
      }
      assertThat(rows).hasSizeGreaterThanOrEqualTo(count);
      return rows;
    }
  }

  @Nested
  class Publication {
    @Test
    void givenTwoPendingEventsWhenPublishingThenKeepsKeyOrderAndMarksAcknowledged() {
      var rentalId = UUID.randomUUID();
      var first = pending(rentalId);
      var second = pending(rentalId);
      assertThat(repository.oldestPendingSeconds()).isPositive();
      assertThat(publisher.publishNext()).isTrue();
      assertThat(publisher.publishNext()).isTrue();
      assertThat(publisher.publishNext()).isFalse();
      assertThat(repository.countByPublishedAtIsNull()).isZero();
      assertThat(repository.oldestPendingSeconds()).isZero();
      var records = read(rentalId, 2);
      assertThat(records)
          .extracting(ConsumerRecord::value)
          .containsExactly(first.payload(), second.payload());
      assertThat(records.get(0).partition()).isEqualTo(records.get(1).partition());
    }

    @Test
    void givenAcknowledgedSendAndDatabaseRollbackWhenRetryingThenSameEventCanArriveTwice() {
      var rentalId = UUID.randomUUID();
      var event = pending(rentalId);
      tx.executeWithoutResult(
          status -> {
            assertThat(publisher.publishNext()).isTrue();
            status.setRollbackOnly();
          });
      assertThat(repository.countByPublishedAtIsNull()).isEqualTo(1);
      assertThat(publisher.publishNext()).isTrue();
      assertThat(read(rentalId, 2))
          .extracting(ConsumerRecord::value)
          .containsExactly(event.payload(), event.payload());
    }

    @Test
    void givenUnavailableBrokerWhenPublishingThenPersistsRetryAndRecovers() {
      var rentalId = UUID.randomUUID();
      var event = pending(rentalId);
      KAFKA.getDockerClient().pauseContainerCmd(KAFKA.getContainerId()).exec();
      try {
        assertThat(publisher.publishNext()).isFalse();
        assertThat(repository.countByPublishedAtIsNull()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select attempts from outbox_messages", Integer.class))
            .isEqualTo(1);
      } finally {
        KAFKA.getDockerClient().unpauseContainerCmd(KAFKA.getContainerId()).exec();
      }
      // Avoid a wall-clock sleep: make the persisted retry due immediately.
      jdbc.update("update outbox_messages set next_attempt_at=current_timestamp");
      org.awaitility.Awaitility.await()
          .atMost(Duration.ofSeconds(30))
          .until(
              () -> {
                jdbc.update(
                    "update outbox_messages set next_attempt_at=current_timestamp where published_at is null");
                return publisher.publishNext();
              });
      assertThat(repository.countByPublishedAtIsNull()).isZero();
      assertThat(read(rentalId, 1)).extracting(ConsumerRecord::value).containsOnly(event.payload());
    }
  }

  @Nested
  class TryPublisherLock {
    @Test
    void givenCompetingTransactionWhenPollingThenOnlyLockOwnerCanPublishAndRollbackReleasesLock()
        throws Exception {
      pending(UUID.randomUUID());
      var acquired = new CountDownLatch(1);
      var release = new CountDownLatch(1);
      try (var executor = Executors.newSingleThreadExecutor()) {
        var owner =
            executor.submit(
                () ->
                    tx.executeWithoutResult(
                        status -> {
                          assertThat(repository.tryPublisherLock()).isTrue();
                          acquired.countDown();
                          try {
                            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                          }
                          status.setRollbackOnly();
                        }));
        try {
          assertThat(acquired.await(10, TimeUnit.SECONDS)).isTrue();
          assertThat(publisher.publishNext()).isFalse();
          assertThat(repository.countByPublishedAtIsNull()).isEqualTo(1);
        } finally {
          release.countDown();
        }
        owner.get(10, TimeUnit.SECONDS);
      }
      assertThat(publisher.publishNext()).isTrue();
    }
  }
}
