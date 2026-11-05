package com.github.theguuholi.rentcars.cars;

import com.fasterxml.jackson.annotation.JsonView;
import com.github.theguuholi.rentcars.shared.http.GlobalExceptionHandler.Errors;
import com.github.theguuholi.rentcars.shared.http.JsonViews;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

  @JsonView(JsonViews.Summary.class)
  @GetMapping
  @Operation(summary = "List all cars alphabetically")
  @ApiResponse(responseCode = "200", useReturnTypeSchema = true)
  public List<Car> list() {
    return cars.list();
  }

  @JsonView(JsonViews.Detail.class)
  @GetMapping("/{id}")
  @Operation(
      summary = "Fetch a car by UUID",
      description =
          "Includes the assigned category and specifications with their IDs, names and descriptions.")
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
  @JsonView(JsonViews.Summary.class)
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
  @JsonView(JsonViews.Summary.class)
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      required = true,
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = CarRequest.UpdateSchema.class)))
  @PatchMapping("/{id}")
  @Operation(
      summary = "Update selected car attributes",
      description =
          "Send the version read when the form opened as expectedVersion. Omitted fields retain their values. License plate is immutable. Supplying specificationIds replaces the full selection.")
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
      responseCode = "409",
      description = "Stale revision. Reopen the editor and retry with the current version.",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
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

  @GetMapping("/{carId}/images")
  @Operation(summary = "List the car gallery in upload order")
  public List<CarImage> listImages(@PathVariable UUID carId) {
    return cars.listImages(carId);
  }

  @GetMapping(value = "/{carId}/images/{imageId}", produces = MediaType.IMAGE_PNG_VALUE)
  @Operation(summary = "Read a public car photo as PNG")
  @ApiResponse(
      responseCode = "200",
      content =
          @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")))
  public ResponseEntity<Resource> readImage(@PathVariable UUID carId, @PathVariable UUID imageId)
      throws IOException {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.IMAGE_PNG)
        .body(cars.readImage(carId, imageId));
  }

  @PostMapping(value = "/{carId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(
      responseCode = "401",
      description = "A valid session is required",
      content = @Content)
  @ApiResponse(
      responseCode = "403",
      description = "Administrator access is required",
      content = @Content)
  @ApiResponse(responseCode = "404", description = "Car or image not found", content = @Content)
  @Operation(
      summary = "Add one car photo",
      description =
          "Admin only. Send an image part containing a JPEG or PNG up to 5 MiB and 4 megapixels. Stored PNGs fit within 1600 × 1600 pixels. Existing images are retained.")
  @ApiResponse(
      responseCode = "201",
      description = "Image added",
      content = @Content(schema = @Schema(implementation = CarImage.class)))
  @ApiResponse(responseCode = "413", description = "Image too large", content = @Content)
  @ApiResponse(responseCode = "422", description = "Invalid image", content = @Content)
  public ResponseEntity<CarImage> addImage(
      @PathVariable UUID carId, @RequestParam("image") MultipartFile image) throws IOException {
    var saved = cars.addImage(carId, image);
    return ResponseEntity.created(URI.create("/cars/" + carId + "/images/" + saved.getId()))
        .body(saved);
  }

  @DeleteMapping("/{carId}/images/{imageId}")
  @ApiResponse(responseCode = "204", description = "Photo removed", content = @Content)
  @SecurityRequirement(name = "bearerAuth")
  @ApiResponse(
      responseCode = "401",
      description = "A valid session is required",
      content = @Content)
  @ApiResponse(
      responseCode = "403",
      description = "Administrator access is required",
      content = @Content)
  @ApiResponse(responseCode = "404", description = "Car or image not found", content = @Content)
  @Operation(
      summary = "Remove one car photo",
      description =
          "Admin only. Returns 404 if the photo does not belong to this car or was already removed.")
  public ResponseEntity<Void> removeImage(@PathVariable UUID carId, @PathVariable UUID imageId) {
    cars.removeImage(carId, imageId);
    return ResponseEntity.noContent().build();
  }
}
