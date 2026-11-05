package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.*;

class CarImageRepositoryTest extends CarImageTestSupport {
  @Nested
  class FindByCarIdOrderByCreatedAtAscIdAsc {
    @Test
    @DisplayName(
        "Given multiple car galleries, when querying one, then return only its images in upload order")
    void order() throws Exception {
      var a = service.addImage(car, png());
      var b = service.addImage(car, png());
      var firstCar = car;
      createCar();
      service.addImage(car, png());
      assertThat(images.findByCarIdOrderByCreatedAtAscIdAsc(firstCar))
          .extracting(CarImage::getId)
          .containsExactly(a.getId(), b.getId());
    }
  }

  @Nested
  class FindByIdAndCarId {
    @Test
    @DisplayName(
        "Given an image attached to a car, when resolving its ID under another car, then do not return it")
    void ownership() throws Exception {
      var image = service.addImage(car, png());
      assertThat(images.findByIdAndCarId(image.getId(), car)).isPresent();
      createCar();
      assertThat(images.findByIdAndCarId(image.getId(), car)).isEmpty();
    }
  }
}
