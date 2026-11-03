package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BrlAmountTest {

  @Nested
  @DisplayName("BrlAmount")
  class Constructor {
    @DisplayName("Given non-negative centavos, the amount retains the exact integer")
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 199, Integer.MAX_VALUE})
    void givenNonNegativeCentavosWhenConstructingThenPreservesTheValue(int cents) {
      assertThat(new BrlAmount(cents).cents()).isEqualTo(cents);
    }

    @DisplayName("Given negative centavos, constructing the amount fails")
    @Test
    void givenNegativeCentavosWhenConstructingThenRejectsTheCharge() {
      assertThatIllegalArgumentException().isThrownBy(() -> new BrlAmount(-1));
    }
  }
}
