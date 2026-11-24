package com.github.theguuholi.rentcars.cars;

import com.fasterxml.jackson.annotation.JsonView;
import com.github.theguuholi.rentcars.shared.http.JsonViews;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Cars")
class CarRecommendationsController {
  private final CarRecommendations recommendations;

  CarRecommendationsController(CarRecommendations recommendations) {
    this.recommendations = recommendations;
  }

  @GetMapping(value = "/cars/recommendations", produces = "application/json")
  @JsonView(JsonViews.Summary.class)
  @Operation(
      summary = "Find the most rented available cars",
      description =
          "Returns up to six currently available cars in the precomputed 30-day popularity order. "
              + "Deleted and unavailable cars are omitted. An empty array means no usable ranking or a temporarily unavailable recommendations service. "
              + "Popularity is not personalized and does not reserve a car.")
  @ApiResponse(responseCode = "200", useReturnTypeSchema = true)
  List<Car> list() {
    return recommendations.listAvailable();
  }
}
