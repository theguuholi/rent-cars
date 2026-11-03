package com.github.theguuholi.rentcars.shared.http;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @Schema(
      description =
          "API errors grouped by field (such as name or description) or by reason (conflict or server).")
  public record Errors(
      @Schema(
              description = "Each key maps to one or more messages that explain what went wrong.",
              requiredMode = Schema.RequiredMode.REQUIRED,
              example = "{\"description\":[\"must not be blank\"]}")
          Map<String, List<String>> errors) {}

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

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Map<String, List<String>> errors = new TreeMap<>();
    exception
        .getFieldErrors()
        .forEach(
            e ->
                errors
                    .computeIfAbsent(e.getField(), key -> new ArrayList<>())
                    .add(e.getDefaultMessage()));
    return ResponseEntity.unprocessableContent().headers(headers).body(new Errors(errors));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Errors> integrity(DataIntegrityViolationException exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      // PostgreSQL reports unique violations with SQLSTATE 23505 for every unique index.
      if (!(cause instanceof SQLException sql)) continue;
      if ("23503".equals(sql.getSQLState())) {
        return ResponseEntity.badRequest()
            .body(new Errors(Map.of("reference", List.of("A referenced record does not exist"))));
      }
      if ("23505".equals(sql.getSQLState())) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(
                new Errors(
                    Map.of(
                        "conflict",
                        List.of("A record with the same unique value already exists"))));
      }
    }
    return unexpected(exception);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Errors> unexpected(Exception exception) {
    log.error("Unexpected API error", exception);
    return ResponseEntity.internalServerError()
        .body(new Errors(Map.of("server", List.of("Unexpected server error"))));
  }
}
