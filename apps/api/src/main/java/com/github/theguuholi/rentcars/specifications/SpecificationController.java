package com.github.theguuholi.rentcars.specifications;

import com.github.theguuholi.rentcars.shared.http.GlobalExceptionHandler.Errors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Specifications", description = "Manage features that describe rental vehicles.")
@ApiResponse(
    responseCode = "500",
    description = "Unexpected server error.",
    content =
        @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = Errors.class),
            examples =
                @ExampleObject(value = "{\"errors\":{\"server\":[\"Unexpected server error\"]}}")))
@RestController
@RequestMapping("/specifications")
public class SpecificationController {
  private final SpecificationService service;

  SpecificationController(SpecificationService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "List specifications alphabetically")
  @ApiResponse(
      responseCode = "200",
      description = "Full catalog in ascending name order.",
      useReturnTypeSchema = true)
  public List<Specification> list() {
    return service.list();
  }

  @GetMapping("/{id}")
  @Operation(summary = "Look up a specification by UUID")
  @ApiResponse(
      responseCode = "200",
      description = "Requested specification.",
      useReturnTypeSchema = true)
  public Specification get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping
  @Operation(
      summary = "Create a specification with a unique name and a description",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              description = "Both name and description are required.",
              content =
                  @Content(
                      mediaType = "application/json",
                      schema = @Schema(implementation = SpecificationRequest.class),
                      examples =
                          @ExampleObject(
                              value =
                                  "{\"name\":\"gps\",\"description\":\"Turn-by-turn satellite navigation.\"}"))))
  @ApiResponse(
      responseCode = "409",
      description = "Another specification already uses this name.",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = Errors.class),
              examples =
                  @ExampleObject(
                      value =
                          "{\"errors\":{\"conflict\":[\"A record with the same unique value already exists\"]}}")))
  @ApiResponse(
      responseCode = "422",
      description = "One or more fields failed validation.",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = Errors.class),
              examples =
                  @ExampleObject(value = "{\"errors\":{\"description\":[\"must not be blank\"]}}")))
  @ApiResponse(
      responseCode = "201",
      description = "Specification created. Location identifies the new resource.",
      useReturnTypeSchema = true)
  public ResponseEntity<Specification> create(@RequestBody SpecificationRequest request) {
    var item = service.create(request);
    return ResponseEntity.created(URI.create("/specifications/" + item.getId())).body(item);
  }

  @RequestMapping(
      path = "/{id}",
      method = {RequestMethod.PUT, RequestMethod.PATCH})
  @Operation(
      summary = "Update a specification",
      description = "Omitted fields retain their current values.",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              description = "Supply only the fields to change. Explicit nulls are invalid.",
              content =
                  @Content(
                      mediaType = "application/json",
                      schema = @Schema(implementation = SpecificationRequest.class),
                      examples =
                          @ExampleObject(
                              value = "{\"description\":\"Offline navigation maps.\"}"))))
  @ApiResponse(
      responseCode = "409",
      description = "Another specification already uses this name.",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = Errors.class),
              examples =
                  @ExampleObject(
                      value =
                          "{\"errors\":{\"conflict\":[\"A record with the same unique value already exists\"]}}")))
  @ApiResponse(
      responseCode = "422",
      description = "One or more fields failed validation.",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = Errors.class),
              examples =
                  @ExampleObject(value = "{\"errors\":{\"description\":[\"must not be blank\"]}}")))
  @ApiResponse(
      responseCode = "200",
      description = "Updated specification.",
      useReturnTypeSchema = true)
  public Specification update(@PathVariable UUID id, @RequestBody SpecificationRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a specification")
  @ApiResponse(responseCode = "204", description = "Specification deleted.", content = @Content)
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
