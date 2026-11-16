package com.github.theguuholi.rentcars.outbox;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OutboxScheduleTest {
  final OutboxPublisher publisher = mock();
  final OutboxSchedule schedule = new OutboxSchedule(publisher);

  @Nested
  class PublishBatch {
    @Test
    void givenBacklogWhenPollingThenLimitsEachBatchToTwentyTransactions() {
      when(publisher.publishNext()).thenReturn(true);
      schedule.publishBatch();
      verify(publisher, times(20)).publishNext();
    }

    @Test
    void givenFailureOrEmptyQueueWhenPollingThenStopsBatchImmediately() {
      when(publisher.publishNext()).thenReturn(true, false);
      schedule.publishBatch();
      verify(publisher, times(2)).publishNext();
    }
  }
}
