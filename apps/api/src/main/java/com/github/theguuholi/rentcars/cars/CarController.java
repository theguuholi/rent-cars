package com.github.theguuholi.rentcars.cars;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Cars", description = "Manage rental vehicles, charges and assigned catalog features.")
@RestController
@RequestMapping("/cars")
public class CarController {
  private final CarService cars;

  CarController(CarService cars) {
    this.cars = cars;
  }

  @GetMapping
  @Operation(summary = "List all cars alphabetically")
  public List<CarResponse> list() {
    return cars.list().stream().map(Car::toResponse).toList();
  }

  @GetMapping("/{id}")
  @Operation(summary = "Fetch a car by UUID")
  @ApiResponse(responseCode = "200", useReturnTypeSchema = true)
  @ApiResponse(responseCode = "404", description = "Car does not exist", content = @Content)
  public CarResponse get(@PathVariable UUID id) {
    return cars.get(id).toResponse();
  }

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
      description = "Invalid request or reference",
      content = @Content)
  @ApiResponse(responseCode = "422", description = "Invalid car attributes", content = @Content)
  @ApiResponse(responseCode = "409", description = "Duplicate license plate", content = @Content)
  public ResponseEntity<CarResponse> create(@RequestBody CarRequest request) {
    CarResponse car = cars.create(request).toResponse();
    return ResponseEntity.created(URI.create("/cars/" + car.id())).body(car);
  }

  @PatchMapping("/{id}")
  @Operation(
      summary = "Update selected car attributes",
      description =
          "Omitted fields retain their values. License plate is immutable. Supplying specificationIds replaces the full selection.")
  @ApiResponse(responseCode = "200", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = "Invalid request or reference",
      content = @Content)
  @ApiResponse(
      responseCode = "422",
      description = "Invalid attributes or new plate",
      content = @Content)
  @ApiResponse(responseCode = "404", description = "Car does not exist", content = @Content)
  public CarResponse update(@PathVariable UUID id, @RequestBody CarRequest request) {
    return cars.update(id, request).toResponse();
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Remove a car")
  @ApiResponse(responseCode = "204", description = "Car removed", content = @Content)
  @ApiResponse(responseCode = "404", description = "Car does not exist", content = @Content)
  public void delete(@PathVariable UUID id) {
    cars.delete(id);
  }
}
