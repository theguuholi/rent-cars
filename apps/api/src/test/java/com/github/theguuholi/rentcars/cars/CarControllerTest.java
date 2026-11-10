package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CarControllerTest {
  private final CarService service = mock();
  private final CarController controller =
      new CarController(
          service, mock(com.github.theguuholi.rentcars.shared.storage.ImageStorage.class));
  private final UUID id = UUID.randomUUID();

  @Nested
  @DisplayName("list()")
  class ListCars {
    @Test
    @DisplayName("Given an empty fleet, when listing, then the public filters reach the service")
    void emptyFleet() {
      var pageable = PageRequest.of(0, 12);
      var empty = new CarPage(java.util.List.of(), 0, 12, 0, 0);
      when(service.list(true, "Argo", "Fiat", "HATCHBACK", pageable)).thenReturn(empty);
      assertThat(controller.list("Argo", "Fiat", "HATCHBACK", pageable)).isSameAs(empty);
    }

    @Test
    @DisplayName("Given an admin, when listing, then unavailable cars are included")
    void manageFleet() {
      var pageable = PageRequest.of(0, 12);
      var page = new CarPage(java.util.List.of(new Car()), 0, 12, 1, 1);
      when(service.list(false, null, null, null, pageable)).thenReturn(page);
      assertThat(controller.manage(null, null, null, pageable)).isSameAs(page);
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
      assertThat(controller.get(id)).isEqualTo(car);
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
      when(saved.id()).thenReturn(id);
      when(service.create(any())).thenReturn(saved);

      var request = new CarRequest();
      request.licensePlate = "ABC1234";
      var response = controller.create(request);

      verify(service).create(request);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("/cars/" + id));
      assertThat(response.getBody()).isEqualTo(saved);
    }

    @Test
    @DisplayName("Given a service rejection, when creating, then the error propagates")
    void rejectedCreation() {
      var conflict = new org.springframework.dao.DataIntegrityViolationException("duplicate plate");
      when(service.create(any())).thenThrow(conflict);
      var body = new CarRequest();
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

      var request = new CarRequest();
      request.available = false;
      assertThat(controller.update(id, request)).isEqualTo(updated);
      verify(service).update(id, request);
    }

    @Test
    @DisplayName("Given an unknown car, when updating, then the missing-car error propagates")
    void missingCar() {
      var missing = new ResponseStatusException(HttpStatus.NOT_FOUND);
      when(service.update(eq(id), any())).thenThrow(missing);
      var body = new CarRequest();
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
