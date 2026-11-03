package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

class CarControllerTest {
  private final CarService service = mock();
  private final CarController controller = new CarController(service);
  private final UUID id = UUID.randomUUID();
  private final JsonMapper json = JsonMapper.builder().build();

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
      var first = new Car();
      var second = new Car();
      when(service.list()).thenReturn(List.of(second, first));
      assertThat(controller.list()).containsExactly(second, first);
    }
  }

  @Nested
  @DisplayName("get()")
  class GetCar {
    @Test
    @DisplayName("Given a known ID, when fetching, then the matching car is returned")
    void knownCar() {
      var car = new Car();
      when(service.get(id)).thenReturn(car);
      assertThat(controller.get(id)).isSameAs(car);
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
      var saved = mock(Car.class);
      when(saved.getId()).thenReturn(id);
      when(service.create(any())).thenReturn(saved);

      var response = controller.create(json.readTree("{\"licensePlate\":\"ABC1234\"}"));

      var captured = ArgumentCaptor.forClass(CarRequest.class);
      verify(service).create(captured.capture());
      assertThat(captured.getValue().licensePlate()).isEqualTo("ABC1234");
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("/cars/" + id));
      assertThat(response.getBody()).isSameAs(saved);
    }

    @Test
    @DisplayName(
        "Given malformed attributes, when creating, then parsing rejects them before the service is called")
    void malformedAttributes() {
      var body = json.readTree("{\"available\":null}");
      assertThatThrownBy(() -> controller.create(body))
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
      verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Given a service rejection, when creating, then the error propagates")
    void rejectedCreation() {
      var conflict = new org.springframework.dao.DataIntegrityViolationException("duplicate plate");
      when(service.create(any())).thenThrow(conflict);
      var body = json.readTree("{}");
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
      var updated = new Car();
      when(service.update(eq(id), any())).thenReturn(updated);

      assertThat(controller.update(id, json.readTree("{\"available\":false}"))).isSameAs(updated);

      var captured = ArgumentCaptor.forClass(CarRequest.class);
      verify(service).update(eq(id), captured.capture());
      assertThat(captured.getValue().available()).isFalse();
      assertThat(captured.getValue().name()).isNull();
    }

    @Test
    @DisplayName(
        "Given malformed attributes, when updating, then parsing rejects them before the service is called")
    void malformedAttributes() {
      var body = json.readTree("{\"unknown\":1}");
      assertThatThrownBy(() -> controller.update(id, body))
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
      verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Given an unknown car, when updating, then the missing-car error propagates")
    void missingCar() {
      var missing = new ResponseStatusException(HttpStatus.NOT_FOUND);
      when(service.update(eq(id), any())).thenThrow(missing);
      var body = json.readTree("{}");
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
