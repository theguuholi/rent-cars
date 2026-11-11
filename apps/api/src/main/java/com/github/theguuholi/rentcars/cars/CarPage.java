package com.github.theguuholi.rentcars.cars;

import com.fasterxml.jackson.annotation.JsonView;
import com.github.theguuholi.rentcars.shared.http.JsonViews;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@JsonView(JsonViews.Summary.class)
@Schema(description = "A page of car summaries and navigation metadata.")
public record CarPage(
    @Schema(description = "Cars on this page.") List<Car> content,
    @Schema(description = "Zero-based page number.", example = "0") int number,
    @Schema(description = "Requested page size.", example = "12") int size,
    @Schema(description = "Total matching cars.", example = "25") long totalElements,
    @Schema(description = "Total matching pages.", example = "3") int totalPages) {
  static CarPage from(Page<Car> page) {
    return new CarPage(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
