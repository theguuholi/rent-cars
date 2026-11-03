package com.github.theguuholi.rentcars.cars;

import com.github.theguuholi.rentcars.shared.http.GlobalExceptionHandler.Errors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Cars", description = "Manage rental vehicles, charges and assigned catalog features.")
@ApiResponse(
    responseCode = "500",
    description = "Unexpected server error",
    content =
        @Content(mediaType = "application/json", schema = @Schema(implementation = Errors.class)))
@RestController
@RequestMapping("/cars")
public class CarController {
  private final CarService cars;

  CarController(CarService cars) {
    this.cars = cars;
  }

  @GetMapping
  @Operation(summary = "List all cars alphabetically")
  @ApiResponse(responseCode = "200", useReturnTypeSchema = true)
  public List<Car> list() {
    return cars.list();
  }

  @GetMapping("/{id}")
  @Operation(summary = "Fetch a car by UUID")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid car UUID",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(responseCode = "200", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "404",
      description = "Car does not exist",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  public Car get(@PathVariable UUID id) {
    return cars.get(id);
  }

  @SecurityRequirement(name = "bearerAuth")
  @PostMapping
  @Operation(
      summary = "Add a car",
      description = "Availability defaults to true and specifications to an empty list.")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      required = true,
      content = @Content(schema = @Schema(implementation = CarRequest.CreateSchema.class)))
  @ApiResponse(responseCode = "201", description = "Car created", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = "Malformed request or missing catalog reference",
      content = {
        @Content(mediaType = "application/json", schema = @Schema(implementation = Errors.class)),
        @Content(
            mediaType = "application/problem+json",
            schema = @Schema(implementation = ProblemDetail.class))
      })
  @ApiResponse(
      responseCode = "422",
      description = "Invalid car attributes",
      content =
          @Content(mediaType = "application/json", schema = @Schema(implementation = Errors.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Duplicate license plate",
      content =
          @Content(mediaType = "application/json", schema = @Schema(implementation = Errors.class)))
  public ResponseEntity<Car> create(@RequestBody CarRequest request) {
    Car car = cars.create(request);
    return ResponseEntity.created(URI.create("/cars/" + car.id())).body(car);
  }

  @SecurityRequirement(name = "bearerAuth")
  @PatchMapping("/{id}")
  @Operation(
      summary = "Update selected car attributes",
      description =
          "Omitted fields retain their values. License plate is immutable. Supplying specificationIds replaces the full selection.")
  @ApiResponse(responseCode = "200", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = "Malformed request or missing catalog reference",
      content = {
        @Content(mediaType = "application/json", schema = @Schema(implementation = Errors.class)),
        @Content(
            mediaType = "application/problem+json",
            schema = @Schema(implementation = ProblemDetail.class))
      })
  @ApiResponse(
      responseCode = "422",
      description = "Invalid attributes or changed license plate",
      content = {
        @Content(mediaType = "application/json", schema = @Schema(implementation = Errors.class)),
        @Content(
            mediaType = "application/problem+json",
            schema = @Schema(implementation = ProblemDetail.class))
      })
  @ApiResponse(
      responseCode = "404",
      description = "Car does not exist",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  public Car update(@PathVariable UUID id, @RequestBody CarRequest request) {
    return cars.update(id, request);
  }

  @SecurityRequirement(name = "bearerAuth")
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Remove a car")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid car UUID",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(responseCode = "204", description = "Car removed", content = @Content)
  @ApiResponse(
      responseCode = "404",
      description = "Car does not exist",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  public void delete(@PathVariable UUID id) {
    cars.delete(id);
  }
}
