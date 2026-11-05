package com.github.theguuholi.rentcars.cars;

import com.fasterxml.jackson.annotation.JsonView;
import com.github.theguuholi.rentcars.shared.http.GlobalExceptionHandler.Errors;
import com.github.theguuholi.rentcars.shared.http.JsonViews;
import com.github.theguuholi.rentcars.shared.storage.ImageStorage;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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
  private final ImageStorage storage;

  CarController(CarService cars, ImageStorage storage) {
    this.cars = cars;
    this.storage = storage;
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
      summary = "Fetch a car or one of its images",
      description =
          "Without imageId, returns car details including category, specifications and images. With imageId, returns that image's JPEG or PNG bytes.",
      parameters =
          @io.swagger.v3.oas.annotations.Parameter(
              name = "imageId",
              in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
              description = "Optional image identifier from the car detail response.",
              schema = @Schema(type = "string", format = "uuid")))
  @ApiResponse(
      responseCode = "400",
      description = "Invalid car UUID",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "200",
      description = "Car details or image bytes",
      content = {
        @Content(mediaType = "application/json", schema = @Schema(implementation = Car.class)),
        @Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
        @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary"))
      })
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
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Add a car with images",
      description =
          "Send car as application/json and repeat the optional images parts. JPEG/PNG, 5 MB per file, 30 MB total. Follow Location for image identifiers.")
  @ApiResponse(responseCode = "201", description = "Car created", useReturnTypeSchema = true)
  @ApiResponse(responseCode = "413", description = "Upload too large")
  public ResponseEntity<Car> createWithImages(
      @RequestPart("car") CarRequest request,
      @RequestPart(value = "images", required = false) List<MultipartFile> files) {
    Car car = cars.create(request, files == null ? List.of() : files);
    return ResponseEntity.created(URI.create("/cars/" + car.id())).body(car);
  }

  @SecurityRequirement(name = "bearerAuth")
  @JsonView(JsonViews.Summary.class)
  @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Update a car and its images",
      description =
          "Send car as application/json including expectedVersion. New images are appended. removeImageIds removes selected existing images. Omitted images are retained.")
  @ApiResponse(responseCode = "200", useReturnTypeSchema = true)
  @ApiResponse(responseCode = "413", description = "Upload too large")
  public Car updateWithImages(
      @PathVariable UUID id,
      @RequestPart("car") CarRequest request,
      @RequestPart(value = "images", required = false) List<MultipartFile> files) {
    return cars.update(id, request, files == null ? List.of() : files);
  }

  // OpenAPI describes both response forms on get(); they share one path and HTTP method.
  @GetMapping(value = "/{id}", params = "imageId")
  @Operation(hidden = true)
  public ResponseEntity<byte[]> image(
      @PathVariable UUID id, @RequestParam(required = false) UUID imageId) {
    CarImage image =
        cars.get(id).images().stream()
            .filter(item -> item.id().equals(imageId))
            .findFirst()
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Car image not found"));
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(image.contentType()))
        .header("X-Content-Type-Options", "nosniff")
        .header("Cache-Control", "no-store")
        .body(storage.load(image.key()));
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
}
