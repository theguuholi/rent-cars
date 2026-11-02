package com.github.theguuholi.rentcars.shared.http;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {
  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void domainValidationReportsTheFieldAndMessage() {
    record Input(@NotBlank(message = "required") String name) {}
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var violations = factory.getValidator().validate(new Input(""));
      assertThat(
              handler
                  .invalid(new jakarta.validation.ConstraintViolationException(violations))
                  .errors())
          .isEqualTo(Map.of("name", List.of("required")));
    }
  }

  @Test
  void collectsAllRequestErrorsForAField() {
    var exception = mock(MethodArgumentNotValidException.class);
    when(exception.getFieldErrors())
        .thenReturn(
            List.of(
                new FieldError("request", "name", "required"),
                new FieldError("request", "name", "invalid")));
    assertThat(
            ((GlobalExceptionHandler.Errors)
                    handler
                        .handleMethodArgumentNotValid(
                            exception, new HttpHeaders(), HttpStatus.BAD_REQUEST, mock())
                        .getBody())
                .errors())
        .isEqualTo(Map.of("name", List.of("required", "invalid")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"specifications_name_index", "categories_name_unique"})
  void recognizedUniqueConstraintsReturnConflict(String constraint) {
    var violation = mock(org.hibernate.exception.ConstraintViolationException.class);
    when(violation.getConstraintName()).thenReturn(constraint);
    var result = handler.integrity(new DataIntegrityViolationException("save failed", violation));
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(result.getBody().errors())
        .isEqualTo(Map.of("name", List.of("has already been taken")));
  }

  @Test
  void unexpectedIntegrityFailureHidesInternalDetails() {
    var violation = mock(org.hibernate.exception.ConstraintViolationException.class);
    when(violation.getConstraintName()).thenReturn("some_other_constraint");
    var result =
        handler.integrity(new DataIntegrityViolationException("private detail", violation));
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(result.getBody().errors())
        .isEqualTo(Map.of("server", List.of("Unexpected server error")));
  }

  @Test
  void unknownApplicationErrorReturnsSafeServerError() {
    var result = handler.unexpected(new IllegalStateException("private detail"));
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(result.getBody())
        .isEqualTo(
            new GlobalExceptionHandler.Errors(
                Map.of("server", List.of("Unexpected server error"))));
  }
}
