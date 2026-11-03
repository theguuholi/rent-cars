package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BrlAmountConverterTest {
  private final BrlAmountConverter converter = new BrlAmountConverter();

  @Test
  void givenAChargeWhenPersistingAndReadingItThenBothSidesUseTheSameIntegerCentavos() {
    var price = new BrlAmount(1001);
    assertThat(converter.convertToDatabaseColumn(price)).isEqualTo(1001);
    assertThat(converter.convertToEntityAttribute(1001)).isEqualTo(price);
  }

  @Test
  void givenAbsentAmountWhenConvertingThenPreservesAbsenceInBothDirections() {
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
  }

  @Test
  void givenNegativePersistedPriceWhenReadingThenRejectsTheInvalidValue() {
    assertThatIllegalArgumentException().isThrownBy(() -> converter.convertToEntityAttribute(-1));
  }
}
