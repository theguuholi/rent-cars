package com.github.theguuholi.rentcars.cars;

import com.github.theguuholi.rentcars.images.ImageNormalizer;
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
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/cars/{carId}/images")
@Tag(
    name = "Car images",
    description = "Public gallery reads; only administrators may upload or remove images.")
class CarImageController {
  private final CarImageService images;

  CarImageController(CarImageService images) {
    this.images = images;
  }

  @GetMapping
  @Operation(summary = "List the car gallery in upload order")
  List<CarImage> list(@PathVariable UUID carId) {
    return images.list(carId);
  }

  @GetMapping(value = "/{imageId}", produces = MediaType.IMAGE_PNG_VALUE)
  @Operation(summary = "Read a public car photo as PNG")
  @ApiResponse(
      responseCode = "200",
      content =
          @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")))
  ResponseEntity<byte[]> read(@PathVariable UUID carId, @PathVariable UUID imageId)
      throws IOException {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.IMAGE_PNG)
        .body(images.read(carId, imageId));
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
  ResponseEntity<CarImage> add(@PathVariable UUID carId, @RequestParam("image") MultipartFile image)
      throws IOException {
    var saved = images.add(carId, ImageNormalizer.normalize(image, 1600));
    return ResponseEntity.created(URI.create("/cars/" + carId + "/images/" + saved.getId()))
        .body(saved);
  }

  @DeleteMapping("/{imageId}")
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
  ResponseEntity<Void> remove(@PathVariable UUID carId, @PathVariable UUID imageId) {
    images.remove(carId, imageId);
    return ResponseEntity.noContent().build();
  }
}
