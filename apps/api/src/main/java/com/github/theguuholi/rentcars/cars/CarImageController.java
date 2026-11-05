package com.github.theguuholi.rentcars.cars;

import com.fasterxml.jackson.annotation.JsonView;
import com.github.theguuholi.rentcars.shared.http.JsonViews;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/cars")
@Tag(name = "Cars")
class CarImageController {
  private final CarImageService images;

  CarImageController(CarImageService images) {
    this.images = images;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @JsonView(JsonViews.Summary.class)
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Add a car, optionally with images",
      description =
          "Send car as application/json and up to six images as repeated images parts. JPEG and PNG only, 5 MB each, 30 MB total including multipart overhead, at most 20 million pixels per image. Read the Location URL for image identifiers.")
  @ApiResponse(responseCode = "201", description = "Car created", useReturnTypeSchema = true)
  @ApiResponse(responseCode = "413", description = "Upload size limit exceeded")
  public ResponseEntity<Car> create(
      @RequestPart("car") CarRequest request,
      @RequestPart(value = "images", required = false) List<MultipartFile> files) {
    Car car = images.create(request, files == null ? List.of() : files);
    return ResponseEntity.created(URI.create("/cars/" + car.id())).body(car);
  }

  @GetMapping("/{carId}/images/{imageId}")
  @Operation(
      summary = "Read a car image",
      description = "Public image bytes; the image must belong to the specified car.")
  @ApiResponse(responseCode = "200", description = "JPEG or PNG bytes")
  @ApiResponse(responseCode = "404", description = "Image not found")
  public ResponseEntity<byte[]> get(@PathVariable UUID carId, @PathVariable UUID imageId) {
    var file = images.get(carId, imageId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.contentType()))
        .header("X-Content-Type-Options", "nosniff")
        .header("Cache-Control", "no-store")
        .body(file.bytes());
  }

  @DeleteMapping("/{carId}/images/{imageId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Remove a car image",
      description =
          "Removes metadata immediately; durable background cleanup removes the file and retries storage failures.")
  @ApiResponse(responseCode = "204", description = "Image removed")
  @ApiResponse(responseCode = "404", description = "Image not found")
  public void delete(@PathVariable UUID carId, @PathVariable UUID imageId) {
    images.delete(carId, imageId);
  }
}
