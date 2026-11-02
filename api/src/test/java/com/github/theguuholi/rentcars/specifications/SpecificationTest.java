package com.github.theguuholi.rentcars.specifications;

import static org.assertj.core.api.Assertions.*;

import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SpecificationTest {
  private static ValidatorFactory factory;

  @BeforeAll
  static void open() {
    factory = Validation.buildDefaultValidatorFactory();
  }

  @AfterAll
  static void close() {
    factory.close();
  }

  @Test
  void normalizesNameAndPreservesOmittedFields() {
    var spec = new Specification();
    var attrs = new SpecificationRequest();
    attrs.name = "gps";
    attrs.description = "Navigation";
    spec.apply(attrs);
    var rename = new SpecificationRequest();
    rename.name = "abs";
    spec.apply(rename);
    assertThat(spec.getName()).isEqualTo("ABS");
    assertThat(spec.getDescription()).isEqualTo("Navigation");
    var describe = new SpecificationRequest();
    describe.description = "Brakes";
    spec.apply(describe);
    assertThat(spec.getName()).isEqualTo("ABS");
    assertThat(spec.getDescription()).isEqualTo("Brakes");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "\t"})
  void requiresBothFields(String blank) {
    var attrs = new SpecificationRequest();
    attrs.name = blank;
    attrs.description = blank;
    var spec = new Specification();
    spec.apply(attrs);
    assertThat(factory.getValidator().validate(spec))
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactlyInAnyOrder("name", "description");
  }

  @Test
  void checksNameLimitAfterCaseConversionAndAcceptsLongDescription() {
    var spec = new Specification();
    var attrs = new SpecificationRequest();
    attrs.name = "x".repeat(255);
    attrs.description = "d".repeat(1024);
    spec.apply(attrs);
    assertThat(factory.getValidator().validate(spec)).isEmpty();
    attrs.name = "x".repeat(254) + "ß";
    spec.apply(attrs);
    assertThat(factory.getValidator().validate(spec))
        .singleElement()
        .satisfies(v -> assertThat(v.getPropertyPath()).hasToString("name"));
  }
}
