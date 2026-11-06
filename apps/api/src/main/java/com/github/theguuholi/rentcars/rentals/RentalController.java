package com.github.theguuholi.rentcars.rentals;

import com.fasterxml.jackson.annotation.JsonView;
import com.github.theguuholi.rentcars.shared.http.JsonViews;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rentals")
@Tag(name = "Rentals", description = "Start, list and return your own immediate rentals.")
@SecurityRequirement(name = "bearerAuth")
@JsonView(JsonViews.Summary.class)
@ApiResponse(responseCode = "401", description = "A valid bearer token is required")
public class RentalController {
  private final RentalService rentals;

  RentalController(RentalService rentals) {
    this.rentals = rentals;
  }

  @PostMapping
  @Operation(
      summary = "Rent an available car",
      description =
          "Starts now for the authenticated account. Expected return must be at least 24 hours ahead. Only one open rental per account and per car.")
  @ApiResponse(
      responseCode = "201",
      description = "Rental created; Location identifies the rental",
      useReturnTypeSchema = true)
  @ApiResponse(responseCode = "400", description = "Malformed JSON, UUID or timestamp")
  @ApiResponse(responseCode = "404", description = "Car not found")
  @ApiResponse(responseCode = "409", description = "Car unavailable or account already renting")
  @ApiResponse(responseCode = "422", description = "Missing fields or invalid return period")
  public ResponseEntity<Rental> create(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody RentalRequest request) {
    Rental rental =
        rentals.create(
            UUID.fromString(jwt.getSubject()), request.carId(), request.expectedReturnDate());
    return ResponseEntity.created(URI.create("/rentals/" + rental.id())).body(rental);
  }

  @GetMapping
  @Operation(summary = "List your rentals, newest first")
  @ApiResponse(responseCode = "200", useReturnTypeSchema = true)
  public List<Rental> list(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return rentals.list(UUID.fromString(jwt.getSubject()));
  }

  @PostMapping("/{id}/return")
  @Operation(
      summary = "Return your rented car",
      description =
          "Charges completed 24-hour periods, with at least one daily charge, plus the fine for each completed day late. Uses current vehicle prices. Server assigns the return time and total; no body is required.")
  @ApiResponse(responseCode = "200", useReturnTypeSchema = true)
  @ApiResponse(responseCode = "400", description = "Malformed rental UUID")
  @ApiResponse(responseCode = "404", description = "Rental not found or owned by another account")
  @ApiResponse(responseCode = "409", description = "Rental already returned")
  @ApiResponse(responseCode = "422", description = "Invalid return time or charge")
  public Rental returnCar(
      @PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    return rentals.returnCar(id, UUID.fromString(jwt.getSubject()));
  }
}
