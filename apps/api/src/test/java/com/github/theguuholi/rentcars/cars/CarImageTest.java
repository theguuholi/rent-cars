package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.*;

class CarImageTest {
  @Nested
  class Creation {
    @Test
    @DisplayName(
        "Given an allocated image ID, when creating a gallery entry, then preserve that immutable ID")
    void id() {
      var id = UUID.randomUUID();
      assertThat(new CarImage(id, UUID.randomUUID()).getId()).isEqualTo(id);
    }
  }
}
