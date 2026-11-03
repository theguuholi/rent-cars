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
import tools.jackson.databind.JsonNode;

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
  public List<Car> list() {
    return cars.list();
  }

  @GetMapping("/{id}")
  @Operation(summary = "Fetch a car by UUID")
  @ApiResponse(responseCode = "200", useReturnTypeSchema = true)
  @ApiResponse(responseCode = "404", description = "Car does not exist", content = @Content)
  public Car get(@PathVariable UUID id) {
    return cars.get(id);
  }

  @PostMapping
  @Operation(
      summary = "Add a car",
      description =
          "Name, description, brand, license plate, both integer-cent charges and category ID are required. Availability defaults to true and specifications to an empty list.")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      required = true,
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = CarRequest.CreateSchema.class)))
  @ApiResponse(responseCode = "201", description = "Car created", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = "Malformed request or missing catalog reference",
      content = @Content)
  @ApiResponse(responseCode = "422", description = "Invalid car attributes", content = @Content)
  @ApiResponse(
      responseCode = "409",
      description = "License plate already registered",
      content = @Content)
  public ResponseEntity<Car> create(@RequestBody JsonNode body) {
    Car car = cars.create(CarRequest.parse(body));
    return ResponseEntity.created(URI.create("/cars/" + car.getId())).body(car);
  }

  @PatchMapping("/{id}")
  @Operation(
      summary = "Update selected car attributes",
      description =
          "Omitted fields retain their values. License plate is immutable. Supplying specificationIds replaces the full selection.")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      required = true,
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = CarRequest.class)))
  @ApiResponse(responseCode = "200", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = "Malformed request or missing catalog reference",
      content = @Content)
  @ApiResponse(
      responseCode = "422",
      description = "Invalid attributes or changed plate",
      content = @Content)
  @ApiResponse(responseCode = "404", description = "Car does not exist", content = @Content)
  public Car update(@PathVariable UUID id, @RequestBody JsonNode body) {
    return cars.update(id, CarRequest.parse(body));
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
