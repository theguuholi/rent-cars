package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CarControllerTest {
  private final CarService service = mock();
  private final CarController controller = new CarController(service);
  private final UUID id = UUID.randomUUID();

  private Car car(UUID id) {
    var entity = mock(Car.class);
    when(entity.toResponse())
        .thenReturn(
            new CarResponse(
                id, "Argo", "Five doors", "Fiat", "ABC1234", true, 100, 0, null, Set.of()));
    return entity;
  }

  @Nested
  @DisplayName("list()")
  class ListCars {
    @Test
    @DisplayName("Given an empty fleet, when listing, then no cars are returned")
    void emptyFleet() {
      when(service.list()).thenReturn(List.of());
      assertThat(controller.list()).isEmpty();
      verify(service).list();
    }

    @Test
    @DisplayName("Given ordered cars, when listing, then the service order is preserved")
    void populatedFleet() {
      var first = car(UUID.randomUUID());
      var second = car(UUID.randomUUID());
      when(service.list()).thenReturn(List.of(second, first));
      assertThat(controller.list()).containsExactly(second.toResponse(), first.toResponse());
    }
  }

  @Nested
  @DisplayName("get()")
  class GetCar {
    @Test
    @DisplayName("Given a known ID, when fetching, then the matching car is returned")
    void knownCar() {
      var car = car(UUID.randomUUID());
      when(service.get(id)).thenReturn(car);
      assertThat(controller.get(id)).isEqualTo(car.toResponse());
    }

    @Test
    @DisplayName("Given an unknown ID, when fetching, then the missing-car error propagates")
    void missingCar() {
      var missing = new ResponseStatusException(HttpStatus.NOT_FOUND);
      when(service.get(id)).thenThrow(missing);
      assertThatThrownBy(() -> controller.get(id)).isSameAs(missing);
    }
  }

  @Nested
  @DisplayName("create()")
  class CreateCar {
    @Test
    @DisplayName(
        "Given accepted attributes, when creating, then the new car and its location are returned")
    void createdCar() {
      var saved = car(id);
      when(service.create(any())).thenReturn(saved);

      var request = new CarRequest(null, null, null, "ABC1234", null, null, null, null, null);
      var response = controller.create(request);

      verify(service).create(request);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("/cars/" + id));
      assertThat(response.getBody()).isEqualTo(saved.toResponse());
    }

    @Test
    @DisplayName("Given a service rejection, when creating, then the error propagates")
    void rejectedCreation() {
      var conflict = new org.springframework.dao.DataIntegrityViolationException("duplicate plate");
      when(service.create(any())).thenThrow(conflict);
      var body = new CarRequest(null, null, null, null, null, null, null, null, null);
      assertThatThrownBy(() -> controller.create(body)).isSameAs(conflict);
    }
  }

  @Nested
  @DisplayName("update()")
  class UpdateCar {
    @Test
    @DisplayName(
        "Given partial attributes, when updating, then only supplied values reach the service for that ID")
    void partialUpdate() {
      var updated = car(UUID.randomUUID());
      when(service.update(eq(id), any())).thenReturn(updated);

      var request = new CarRequest(null, null, null, null, false, null, null, null, null);
      assertThat(controller.update(id, request)).isEqualTo(updated.toResponse());
      verify(service).update(id, request);
    }

    @Test
    @DisplayName("Given an unknown car, when updating, then the missing-car error propagates")
    void missingCar() {
      var missing = new ResponseStatusException(HttpStatus.NOT_FOUND);
      when(service.update(eq(id), any())).thenThrow(missing);
      var body = new CarRequest(null, null, null, null, null, null, null, null, null);
      assertThatThrownBy(() -> controller.update(id, body)).isSameAs(missing);
    }
  }

  @Nested
  @DisplayName("delete()")
  class DeleteCar {
    @Test
    @DisplayName("Given an existing car, when deleting, then the requested ID is removed")
    void existingCar() {
      controller.delete(id);
      verify(service).delete(id);
    }

    @Test
    @DisplayName("Given an unknown ID, when deleting, then the missing-car error propagates")
    void missingCar() {
      var missing = new ResponseStatusException(HttpStatus.NOT_FOUND);
      doThrow(missing).when(service).delete(id);
      assertThatThrownBy(() -> controller.delete(id)).isSameAs(missing);
    }
  }
}
