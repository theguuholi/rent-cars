package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CarRecommendationsTest {
  private final PopularCarsClient rankings = mock(PopularCarsClient.class);
  private final CarRepository cars = mock(CarRepository.class);
  private final CarRecommendations recommendations = new CarRecommendations(rankings, cars);

  @Nested
  class ListAvailable {
    @Test
    @DisplayName(
        "Given no ranking, when listing recommendations, then no database lookup is needed")
    void empty() {
      when(rankings.carIds()).thenReturn(List.of());
      assertThat(recommendations.listAvailable()).isEmpty();
      verifyNoInteractions(cars);
    }

    @Test
    @DisplayName(
        "Given stale and duplicate ranked IDs, when listing recommendations, then only current cars remain in ranking order")
    void ranked() {
      var first = mock(Car.class);
      var second = mock(Car.class);
      var firstId = UUID.randomUUID();
      var secondId = UUID.randomUUID();
      var stale = UUID.randomUUID();
      when(first.id()).thenReturn(firstId);
      when(second.id()).thenReturn(secondId);
      when(rankings.carIds()).thenReturn(List.of(firstId, stale, secondId, firstId));
      when(cars.findAllByIdInAndAvailableTrue(List.of(firstId, stale, secondId)))
          .thenReturn(List.of(second, first));
      assertThat(recommendations.listAvailable()).containsExactly(first, second);
    }

    @Test
    @DisplayName(
        "Given more than six available ranked cars, when listing recommendations, then only the first six are returned")
    void bounded() {
      var ranked =
          IntStream.range(0, 8)
              .mapToObj(index -> {
                var car = mock(Car.class);
                when(car.id()).thenReturn(UUID.randomUUID());
                return car;
              })
              .toList();
      var ids = ranked.stream().map(Car::id).toList();
      when(rankings.carIds()).thenReturn(ids);
      when(cars.findAllByIdInAndAvailableTrue(ids)).thenReturn(ranked);
      assertThat(recommendations.listAvailable()).containsExactlyElementsOf(ranked.subList(0, 6));
    }
  }
}
