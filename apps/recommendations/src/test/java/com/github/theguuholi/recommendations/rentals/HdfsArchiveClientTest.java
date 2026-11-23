package com.github.theguuholi.recommendations.rentals;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

class HdfsArchiveClientTest {
  final RestClient.Builder builder = RestClient.builder().baseUrl("http://hdfs:14000");
  final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  final HdfsArchiveClient client = new HdfsArchiveClient(builder.build(), "hadoop");

  @AfterEach
  void verify() {
    server.verify();
  }

  @Nested
  class Read {
    @Test
    void givenUtf8ArchiveWhenReadThenPreservesAccents() {
      server
          .expect(requestTo("http://hdfs:14000/webhdfs/v1/events/id.json?op=OPEN&user.name=hadoop"))
          .andExpect(method(HttpMethod.GET))
          .andRespond(
              withSuccess(
                  "locação 🚗".getBytes(StandardCharsets.UTF_8),
                  MediaType.APPLICATION_OCTET_STREAM));
      assertThat(client.read("/events/id.json")).contains("locação 🚗");
    }

    @Test
    void givenMissingFileWhenReadThenReturnsEmpty() {
      server.expect(anything()).andRespond(withStatus(HttpStatus.NOT_FOUND));
      assertThat(client.read("/events/missing.json")).isEmpty();
    }

    @Test
    void givenUnavailableStorageWhenReadThenPropagatesFailure() {
      server.expect(anything()).andRespond(withServerError());
      assertThatThrownBy(() -> client.read("/events/id.json"))
          .isInstanceOf(HttpServerErrorException.class);
    }
  }

  @Nested
  class Create {
    @Test
    void givenUtf8EventWhenCreatingThenWritesExactBytesWithoutOverwrite() {
      server
          .expect(
              requestTo(
                  "http://hdfs:14000/webhdfs/v1/events/.id.tmp?op=CREATE&user.name=hadoop&data=true&overwrite=false"))
          .andExpect(method(HttpMethod.PUT))
          .andExpect(content().bytes("locação 🚗".getBytes(StandardCharsets.UTF_8)))
          .andRespond(withStatus(HttpStatus.CREATED));
      client.create("/events/.id.tmp", "locação 🚗");
    }
  }

  @Nested
  class Rename {
    @Test
    void givenCompletedFileWhenRenamedThenReportsSuccessfulPublication() {
      server
          .expect(
              requestTo(
                  "http://hdfs:14000/webhdfs/v1/events/.id.tmp?op=RENAME&user.name=hadoop&destination=/events/id.json"))
          .andExpect(method(HttpMethod.PUT))
          .andRespond(withSuccess("{\"boolean\":true}", MediaType.APPLICATION_JSON));
      assertThat(client.rename("/events/.id.tmp", "/events/id.json")).isTrue();
    }

    @Test
    void givenOccupiedDestinationWhenRenamedThenReportsLostRace() {
      server
          .expect(anything())
          .andRespond(withSuccess("{\"boolean\":false}", MediaType.APPLICATION_JSON));
      assertThat(client.rename("/events/.id.tmp", "/events/id.json")).isFalse();
    }
  }

  @Nested
  class Delete {
    @Test
    void givenTemporaryFileWhenCleanedThenDeletesOnlyThatPath() {
      server
          .expect(
              requestTo("http://hdfs:14000/webhdfs/v1/events/.id.tmp?op=DELETE&user.name=hadoop"))
          .andExpect(method(HttpMethod.DELETE))
          .andRespond(withSuccess("{\"boolean\":true}", MediaType.APPLICATION_JSON));
      client.delete("/events/.id.tmp");
    }
  }
}
