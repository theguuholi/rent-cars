package com.github.theguuholi.rentcars.rentals;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class RentalEventRecorderIntegrationTest extends RentalIntegrationSupport {
  @Autowired TransactionTemplate tx;
  @Autowired ObjectMapper json;
  @Autowired RentalEventRecorder recorder;

  @BeforeEach
  void clearOutbox() {
    jdbc.update("delete from outbox_messages");
  }

  @Nested
  class Record {
    @Test
    void givenRentalLifecycleWhenCommittingThenStoresVersionedSnapshotsInOrder() {
      var rental = service.create(owner, carId, NOW.plusSeconds(86400));
      service.returnCar(rental.id(), owner);
      var rows =
          jdbc.queryForList("select payload from outbox_messages order by sequence", String.class);
      assertThat(rows).hasSize(2);
      var opened = json.readTree(rows.get(0));
      var returned = json.readTree(rows.get(1));
      assertThat(opened.get("eventType").asText()).isEqualTo("RentalOpened");
      assertThat(returned.get("eventType").asText()).isEqualTo("RentalReturned");
      assertThat(opened.get("eventId").asText()).isNotEqualTo(returned.get("eventId").asText());
      assertThat(opened.get("rentalId").asText()).isEqualTo(rental.id().toString());
      assertThat(returned.get("rentalId").asText()).isEqualTo(rental.id().toString());
      assertThat(
              jdbc.queryForList(
                  "select event_id::text from outbox_messages order by sequence", String.class))
          .containsExactly(opened.get("eventId").asText(), returned.get("eventId").asText());
    }

    @Test
    void givenOuterRollbackWhenOpeningThenNeitherRentalReservationNorEventSurvives() {
      tx.executeWithoutResult(
          status -> {
            service.create(owner, carId, NOW.plusSeconds(86400));
            status.setRollbackOnly();
          });
      assertThat(jdbc.queryForObject("select count(*) from outbox_messages", Integer.class))
          .isZero();
      assertThat(repository.findByAccountIdOrderByStartDateDescIdDesc(owner)).isEmpty();
      assertThat(available(carId)).isTrue();
    }

    @Test
    void givenOuterRollbackWhenReturningThenOnlyOpenedEventAndOpenRentalSurvive() {
      var rental = service.create(owner, carId, NOW.plusSeconds(86400));
      tx.executeWithoutResult(
          status -> {
            service.returnCar(rental.id(), owner);
            status.setRollbackOnly();
          });
      assertThat(jdbc.queryForObject("select count(*) from outbox_messages", Integer.class))
          .isEqualTo(1);
      assertThat(repository.existsByAccountIdAndEndDateIsNull(owner)).isTrue();
      assertThat(available(carId)).isFalse();
    }

    @Test
    void givenNoTransactionWhenRecordingThenRejectsIndependentOutboxWrite() {
      assertThatThrownBy(() -> recorder.opened(null, owner))
          .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }
  }
}
