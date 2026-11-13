package com.github.theguuholi.rentcars.rentals;

import static org.assertj.core.api.Assertions.*;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RentalObservationTest extends RentalIntegrationSupport {
  @Autowired MeterRegistry meters;
  @Autowired ObservationRegistry observations;

  private List<String> capturedContextualNames() {
    List<String> names = new ArrayList<>();
    observations
        .observationConfig()
        .observationHandler(
            new ObservationHandler<Observation.Context>() {
              @Override
              public void onStop(Observation.Context context) {
                names.add(context.getContextualName());
              }

              @Override
              public boolean supportsContext(Observation.Context context) {
                return true;
              }
            });
    return names;
  }

  private long count(String name) {
    return meters.find(name).timers().stream().mapToLong(Timer::count).sum();
  }

  @Nested
  class Create {
    @Test
    void givenSuccessfulAndRejectedRentalsWhenCreatingThenBothOperationsAreObserved() {
      List<String> spanNames = capturedContextualNames();
      long before = count("rental.start");
      service.create(owner, carId, NOW.plusSeconds(86400));
      assertThat(count("rental.start")).isEqualTo(before + 1);
      assertThat(spanNames).contains("rental.start");

      var anotherCar = car();
      assertThatThrownBy(() -> service.create(owner, anotherCar, NOW.plusSeconds(86400)))
          .isInstanceOf(RentalException.class);
      assertThat(count("rental.start")).isEqualTo(before + 2);
    }
  }

  @Nested
  class ReturnCar {
    @Test
    void givenSuccessfulAndRepeatedReturnsWhenReturningThenBothOperationsAreObserved() {
      var rental = service.create(owner, carId, NOW.plusSeconds(86400));
      List<String> spanNames = capturedContextualNames();
      long before = count("rental.return");
      service.returnCar(rental.id(), owner);
      assertThat(count("rental.return")).isEqualTo(before + 1);
      assertThat(spanNames).contains("rental.return");

      assertThatThrownBy(() -> service.returnCar(rental.id(), owner))
          .isInstanceOf(RentalException.class);
      assertThat(count("rental.return")).isEqualTo(before + 2);
    }
  }
}
