package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

class CarControllerTest {
  @Test
  void givenAcceptedCarWhenCreatingThenReturnsThePersistedRepresentationAndItsLocation() {
    var service = mock(CarService.class);
    var saved = mock(Car.class);
    var id = UUID.randomUUID();
    when(saved.getId()).thenReturn(id);
    when(service.create(any())).thenReturn(saved);
    var controller = new CarController(service);
    var body = JsonMapper.builder().build().readTree("{\"licensePlate\":\"ABC1234\"}");

    var response = controller.create(body);

    var captured = ArgumentCaptor.forClass(CarRequest.class);
    verify(service).create(captured.capture());
    assertThat(captured.getValue().licensePlate).isEqualTo("ABC1234");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().getLocation()).hasPath("/cars/" + id);
    assertThat(response.getBody()).isSameAs(saved);
  }
}
