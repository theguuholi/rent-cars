package com.github.theguuholi.rentcars.specifications;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Specifications", description = "Manage features that describe rental vehicles.")
@RestController
@RequestMapping("/specifications")
public class SpecificationController {
  public record Result<T>(T data) {}

  private final SpecificationService service;

  SpecificationController(SpecificationService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "List specifications alphabetically")
  public Result<List<Specification>> list() {
    return new Result<>(service.list());
  }

  @GetMapping("/{id}")
  @Operation(summary = "Look up a specification by UUID")
  public Result<Specification> get(@PathVariable UUID id) {
    return new Result<>(service.get(id));
  }

  @PostMapping
  @Operation(summary = "Create a specification with a unique name and a description")
  public ResponseEntity<Result<Specification>> create(
      @Valid @RequestBody SpecificationRequest request) {
    var item = service.create(request.specification());
    return ResponseEntity.created(URI.create("/specifications/" + item.getId()))
        .body(new Result<>(item));
  }

  @RequestMapping(
      path = "/{id}",
      method = {RequestMethod.PUT, RequestMethod.PATCH})
  @Operation(
      summary = "Update a specification",
      description = "Omitted fields retain their current values.")
  public Result<Specification> update(
      @PathVariable UUID id, @Valid @RequestBody SpecificationRequest request) {
    return new Result<>(service.update(id, request.specification()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a specification")
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }

  public record Errors(Map<String, List<String>> errors) {}

  @ExceptionHandler(ConstraintViolationException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
  Errors invalid(ConstraintViolationException exception) {
    Map<String, List<String>> errors = new TreeMap<>();
    exception
        .getConstraintViolations()
        .forEach(
            v ->
                errors
                    .computeIfAbsent(v.getPropertyPath().toString(), key -> new ArrayList<>())
                    .add(v.getMessage()));
    return new Errors(errors);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
  Errors invalidBody(MethodArgumentNotValidException exception) {
    Map<String, List<String>> errors = new TreeMap<>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(
            e ->
                errors
                    .computeIfAbsent(e.getField(), key -> new ArrayList<>())
                    .add(e.getDefaultMessage()));
    return new Errors(errors);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
  Errors duplicate(DataIntegrityViolationException exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
          && "specifications_name_index".equals(violation.getConstraintName())) {
        return new Errors(Map.of("name", List.of("has already been taken")));
      }
    }
    throw exception;
  }
}
