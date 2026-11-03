package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BrlAmountConverterTest {
  private final BrlAmountConverter converter = new BrlAmountConverter();

  @Nested
  @DisplayName("convertToDatabaseColumn")
  class ConvertToDatabaseColumn {
    @DisplayName("Given a charge, persistence writes its exact centavos")
    @Test
    void givenAChargeWhenWritingThenStoresCentavos() {
      assertThat(converter.convertToDatabaseColumn(new BrlAmount(1001))).isEqualTo(1001);
    }

    @DisplayName("Given an absent charge, persistence writes null")
    @Test
    void givenNoChargeWhenWritingThenStoresNull() {
      assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }
  }

  @Nested
  @DisplayName("convertToEntityAttribute")
  class ConvertToEntityAttribute {
    @DisplayName("Given stored centavos, conversion recovers the exact charge")
    @Test
    void givenStoredCentavosWhenReadingThenRecoversTheCharge() {
      assertThat(converter.convertToEntityAttribute(1001)).isEqualTo(new BrlAmount(1001));
    }

    @DisplayName("Given a null column, conversion returns null")
    @Test
    void givenNullColumnWhenReadingThenReturnsNull() {
      assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @DisplayName("Given a negative column, conversion rejects the invalid charge")
    @Test
    void givenNegativeColumnWhenReadingThenRejectsTheCharge() {
      assertThatIllegalArgumentException().isThrownBy(() -> converter.convertToEntityAttribute(-1));
    }
  }
}
