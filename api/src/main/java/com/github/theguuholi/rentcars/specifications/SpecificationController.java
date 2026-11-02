package com.github.theguuholi.rentcars.specifications;

import com.github.theguuholi.rentcars.shared.http.DataResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Specifications", description = "Manage features that describe rental vehicles.")
@RestController
@RequestMapping("/specifications")
public class SpecificationController {
  private final SpecificationService service;

  SpecificationController(SpecificationService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "List specifications alphabetically")
  public DataResponse<List<Specification>> list() {
    return new DataResponse<>(service.list());
  }

  @GetMapping("/{id}")
  @Operation(summary = "Look up a specification by UUID")
  public DataResponse<Specification> get(@PathVariable UUID id) {
    return new DataResponse<>(service.get(id));
  }

  @PostMapping
  @Operation(summary = "Create a specification with a unique name and a description")
  public ResponseEntity<DataResponse<Specification>> create(
      @Valid @RequestBody SpecificationRequest request) {
    var item = service.create(request.specification());
    return ResponseEntity.created(URI.create("/specifications/" + item.getId()))
        .body(new DataResponse<>(item));
  }

  @RequestMapping(
      path = "/{id}",
      method = {RequestMethod.PUT, RequestMethod.PATCH})
  @Operation(
      summary = "Update a specification",
      description = "Omitted fields retain their current values.")
  public DataResponse<Specification> update(
      @PathVariable UUID id, @Valid @RequestBody SpecificationRequest request) {
    return new DataResponse<>(service.update(id, request.specification()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a specification")
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
