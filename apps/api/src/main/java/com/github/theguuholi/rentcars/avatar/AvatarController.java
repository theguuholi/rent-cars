package com.github.theguuholi.rentcars.avatar;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/users/me/avatar")
@Tag(name = "Account avatar")
@ApiResponse(responseCode = "401", description = "A valid session is required", content = @Content)
@SecurityRequirement(name = "bearerAuth")
class AvatarController {
  private final AvatarService avatars;

  AvatarController(AvatarService avatars) {
    this.avatars = avatars;
  }

  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Private normalized PNG",
        content =
            @Content(
                mediaType = "image/png",
                schema =
                    @io.swagger.v3.oas.annotations.media.Schema(
                        type = "string",
                        format = "binary"))),
    @ApiResponse(
        responseCode = "404",
        description = "No avatar exists for this account",
        content = @Content)
  })
  @GetMapping(produces = MediaType.IMAGE_PNG_VALUE)
  @Operation(summary = "Read your private avatar as PNG (404 when absent)")
  ResponseEntity<byte[]> read(@AuthenticationPrincipal Jwt jwt) throws IOException {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.IMAGE_PNG)
        .body(avatars.read(UUID.fromString(jwt.getSubject())));
  }

  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Avatar replaced", content = @Content),
    @ApiResponse(responseCode = "413", description = "Image exceeds 5 MiB", content = @Content),
    @ApiResponse(
        responseCode = "422",
        description = "Image is not a valid JPEG or PNG of up to 4 megapixels",
        content = @Content)
  })
  @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Replace your avatar",
      description =
          "Send one JPEG or PNG in the image part, up to 5 MiB and 4 megapixels. The stored PNG fits within 512 × 512 pixels.")
  void replace(@AuthenticationPrincipal Jwt jwt, @RequestParam("image") MultipartFile image)
      throws IOException {
    avatars.replace(UUID.fromString(jwt.getSubject()), AvatarImage.normalize(image));
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Remove your avatar")
  void remove(@AuthenticationPrincipal Jwt jwt) {
    avatars.remove(UUID.fromString(jwt.getSubject()));
  }
}
