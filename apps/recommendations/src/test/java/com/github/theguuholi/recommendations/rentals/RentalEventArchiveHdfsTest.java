package com.github.theguuholi.recommendations.rentals;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class RentalEventArchiveHdfsTest {
  @Container static final HdfsTestContainer HDFS = new HdfsTestContainer();
  final JsonMapper json = JsonMapper.builder().build();
  final UUID id = UUID.randomUUID();
  final String root = "/study";
  final String destination = root + "/" + id.toString().substring(0, 2) + "/" + id + ".json";
  final String payload = "{\"eventId\":\"" + id + "\",\"description\":\"locação 🚗\"}";

  RentalEventArchive archive() {
    return new HdfsArchiveConfiguration().rentalEventArchive(json, HDFS.endpoint(), "hadoop", root);
  }

  HdfsArchiveClient client() {
    return new HdfsArchiveClient(RestClient.create(HDFS.endpoint()), "hadoop");
  }

  @Nested
  class Store {
    @Test
    void givenDuplicateEventWhenArchivedThenStoresOriginalUtf8Once() {
      archive().store(id, payload);
      archive().store(id, payload);
      assertThat(client().read(destination)).contains(payload);
    }

    @Test
    void givenSameIdWithChangedContentWhenArchivedThenRejectsWithoutOverwriting() {
      archive().store(id, payload);
      String changed = payload.replace("locação 🚗", "other date");
      var archive = archive();
      assertThatThrownBy(() -> archive.store(id, changed))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(client().read(destination)).contains(payload);
    }

    @Test
    void givenTwoConcurrentWritersWhenArchivingThenPublishesOneCompleteFile() throws Exception {
      var start = new CountDownLatch(1);
      try (var workers = Executors.newFixedThreadPool(2)) {
        var first =
            workers.submit(() -> {
              start.await();
              archive().store(id, payload);
              return true;
            });
        var second =
            workers.submit(() -> {
              start.await();
              archive().store(id, payload);
              return true;
            });
        start.countDown();
        assertThat(first.get(30, TimeUnit.SECONDS)).isTrue();
        assertThat(second.get(30, TimeUnit.SECONDS)).isTrue();
      }
      assertThat(client().read(destination)).contains(payload);
      String folder = destination.substring(0, destination.lastIndexOf('/'));
      var listing =
          RestClient.create(HDFS.endpoint())
              .get()
              .uri("/webhdfs/v1" + folder + "?op=LISTSTATUS&user.name=hadoop")
              .retrieve()
              .body(String.class);
      assertThat(listing).doesNotContain(".tmp");
    }

    @Test
    void givenArchivedEventWhenHdfsRestartsThenRetainsFileAndAcceptsReplay() {
      archive().store(id, payload);
      HDFS.getDockerClient().restartContainerCmd(HDFS.getContainerId()).exec();
      await()
          .ignoreExceptions()
          .atMost(Duration.ofSeconds(90))
          .untilAsserted(() -> assertThat(client().read(destination)).contains(payload));
      archive().store(id, payload);
      assertThat(client().read(destination)).contains(payload);
    }
  }
}
