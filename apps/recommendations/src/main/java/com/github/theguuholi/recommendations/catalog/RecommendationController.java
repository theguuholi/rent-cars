package com.github.theguuholi.recommendations.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RecommendationController {
  private final RecommendationRepository snapshots;

  RecommendationController(RecommendationRepository snapshots) {
    this.snapshots = snapshots;
  }

  @Operation(
      summary = "Read the latest completed popularity ranking",
      description =
          "Counts distinct rentals opened in the preceding 30 days. Empty items before the first successful batch. Results are historical, not current availability.",
      security = @SecurityRequirement(name = "serviceCredentials"))
  @GetMapping("/recommendations/cars")
  RecommendationSnapshot.Result list(
      @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit) {
    return snapshots
        .findFirstByOrderByAsOfDesc()
        .map(snapshot -> snapshot.result(limit))
        .orElseGet(RecommendationSnapshot.Result::empty);
  }
}
