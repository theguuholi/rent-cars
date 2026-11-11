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
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
  @Operation(
      summary = "Search available cars",
      description =
          "Name, brand and category filters match case-insensitive text fragments. Page numbering starts at zero; page size is limited to 100. Sort supports name, brand and id.")
  @ApiResponse(responseCode = "200", useReturnTypeSchema = true)
  @ApiResponse(responseCode = "400", description = "Invalid page size or sort")
  public CarPage list(
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String brand,
      @RequestParam(required = false) String category,
      @PageableDefault(size = 12, sort = "name") Pageable pageable) {
    return cars.list(true, name, brand, category, pageable);
  }

  @SecurityRequirement(name = "bearerAuth")
  @JsonView(JsonViews.Summary.class)
  @GetMapping("/manage")
  @Operation(
      summary = "Search all cars for fleet administration",
      description = "Includes available and unavailable cars. Requires an administrator token.")
  @ApiResponse(responseCode = "200", useReturnTypeSchema = true)
  public CarPage manage(
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String brand,
      @RequestParam(required = false) String category,
      @PageableDefault(size = 12, sort = "name") Pageable pageable) {
    return cars.list(false, name, brand, category, pageable);
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
  @JsonView(JsonViews.Detail.class)
  @PatchMapping(
      value = "/{id}/images",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Upload images for an existing car",
      description =
          "Send one or more repeated images file parts. Appends JPEG/PNG images without replacing existing ones. Maximum 5 MB per file and 30 MB per request. No car JSON or edit version is required.")
  @ApiResponse(
      responseCode = "201",
      description = "Images created; Location points to the car",
      useReturnTypeSchema = true)
  @ApiResponse(responseCode = "400", description = "Missing images part or invalid UUID")
  @ApiResponse(responseCode = "404", description = "Car not found")
  @ApiResponse(responseCode = "413", description = "Upload too large")
  @ApiResponse(responseCode = "422", description = "Invalid image contents")
  public ResponseEntity<Car> uploadImages(
      @PathVariable UUID id, @RequestPart("images") List<MultipartFile> files) {
    Car car = cars.uploadImages(id, files);
    return ResponseEntity.created(URI.create("/cars/" + car.id())).body(car);
  }

  @SecurityRequirement(name = "bearerAuth")
  @DeleteMapping("/{id}/images/{imageId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Remove an image from a car",
      description =
          "The image must belong to the specified car. Removes its metadata and attempts file deletion after the database commits.")
  @ApiResponse(responseCode = "204", description = "Image removed", content = @Content)
  @ApiResponse(responseCode = "404", description = "Car or image not found")
  public void deleteImage(@PathVariable UUID id, @PathVariable UUID imageId) {
    cars.deleteImage(id, imageId);
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
