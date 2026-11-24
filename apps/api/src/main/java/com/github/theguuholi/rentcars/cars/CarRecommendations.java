package com.github.theguuholi.rentcars.cars;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
class CarRecommendations {
  private final PopularCarsClient rankings;
  private final CarRepository cars;

  CarRecommendations(PopularCarsClient rankings, CarRepository cars) {
    this.rankings = rankings;
    this.cars = cars;
  }

  List<Car> listAvailable() {
    // Fetch first so no database transaction waits for the remote service.
    var rankedIds = rankings.carIds().stream().distinct().toList();
    if (rankedIds.isEmpty()) return List.of();
    var available =
        cars.findAllByIdInAndAvailableTrue(rankedIds).stream()
            .collect(Collectors.toMap(Car::id, Function.identity()));
    return rankedIds.stream().map(available::get).filter(Objects::nonNull).limit(6).toList();
  }
}
