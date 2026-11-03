package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BrlAmountTest {
  @ParameterizedTest
  @ValueSource(ints = {0, 1, 199, Integer.MAX_VALUE})
  void givenWholeCentavosWhenCreatingAnAmountThenPreservesTheExactValue(int cents) {
    assertThat(new BrlAmount(cents).cents()).isEqualTo(cents);
  }

  @Test
  void givenNegativeCentavosWhenCreatingAnAmountThenRejectsTheCharge() {
    assertThatIllegalArgumentException().isThrownBy(() -> new BrlAmount(-1));
  }
}
