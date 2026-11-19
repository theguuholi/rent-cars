package com.github.theguuholi.recommendations.rentals;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RentalEventArchiveTest {
  final HdfsArchiveClient hdfs = mock(HdfsArchiveClient.class);
  final RentalEventArchive archive =
      new RentalEventArchive(hdfs, JsonMapper.builder().build(), "/events");
  final UUID id = UUID.fromString("94f25dd5-b8ef-4b41-8d33-72c97cbac937");
  final String destination = "/events/94/" + id + ".json";
  final String payload = "{\"eventId\":\"" + id + "\",\"value\":1}";

  @Nested
  class Store {
    @Test
    void givenNewEventWhenStoredThenPublishesTemporaryFileWithoutOverwriting() {
      when(hdfs.read(destination)).thenReturn(Optional.empty());
      when(hdfs.rename(anyString(), eq(destination))).thenReturn(true);
      archive.store(id, payload);
      var paths = org.mockito.ArgumentCaptor.forClass(String.class);
      var order = inOrder(hdfs);
      order.verify(hdfs).read(destination);
      order.verify(hdfs).create(paths.capture(), eq(payload));
      String temporary = paths.getValue();
      assertThat(temporary).startsWith("/events/94/.").endsWith(".tmp");
      order.verify(hdfs).rename(temporary, destination);
      order.verify(hdfs).delete(temporary);
    }

    @Test
    void givenIdenticalEventWithDifferentJsonFormattingWhenReplayedThenDoesNotWriteAgain() {
      when(hdfs.read(destination))
          .thenReturn(Optional.of("{\"value\":1, \"eventId\":\"" + id + "\"}"));
      archive.store(id, payload);
      verify(hdfs, never()).create(anyString(), anyString());
    }

    @Test
    void givenSameIdWithDifferentContentWhenReplayedThenRejectsWithoutOverwriting() {
      when(hdfs.read(destination)).thenReturn(Optional.of("{\"value\":2}"));
      assertThatThrownBy(() -> archive.store(id, payload))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(id.toString());
      verify(hdfs, never()).create(anyString(), anyString());
    }

    @Test
    void givenConcurrentIdenticalWriteWhenRenameLosesThenAcceptsExistingFileAndCleansTemporary() {
      when(hdfs.read(destination)).thenReturn(Optional.empty(), Optional.of(payload));
      when(hdfs.rename(anyString(), eq(destination))).thenReturn(false);
      archive.store(id, payload);
      verify(hdfs).delete(anyString());
    }

    @Test
    void givenConcurrentConflictingWriteWhenRenameLosesThenRejectsAndCleansTemporary() {
      when(hdfs.read(destination)).thenReturn(Optional.empty(), Optional.of("{}"));
      assertThatThrownBy(() -> archive.store(id, payload))
          .isInstanceOf(IllegalArgumentException.class);
      verify(hdfs).delete(anyString());
    }

    @Test
    void givenFailedRenameWithoutExistingDestinationWhenStoringThenFailsForRetry() {
      when(hdfs.read(destination)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> archive.store(id, payload))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("publish");
      verify(hdfs).delete(anyString());
    }

    @Test
    void givenCleanupFailureAfterPublicationWhenStoredThenFailsAndReplayAcceptsFinalFile() {
      when(hdfs.read(destination)).thenReturn(Optional.empty(), Optional.of(payload));
      when(hdfs.rename(anyString(), eq(destination))).thenReturn(true);
      var failure = new IllegalStateException("cleanup unavailable");
      doThrow(failure).when(hdfs).delete(anyString());
      assertThatThrownBy(() -> archive.store(id, payload)).isSameAs(failure);
      archive.store(id, payload);
      verify(hdfs, times(1)).create(anyString(), eq(payload));
    }

    @Test
    void givenInterruptedWriteWhenStoringThenNeverPublishesPartialFile() {
      when(hdfs.read(destination)).thenReturn(Optional.empty());
      var failure = new IllegalStateException("connection lost");
      doThrow(failure).when(hdfs).create(anyString(), anyString());
      assertThatThrownBy(() -> archive.store(id, payload)).isSameAs(failure);
      verify(hdfs, never()).rename(anyString(), anyString());
      verify(hdfs).delete(anyString());
    }
  }
}
