package com.github.theguuholi.rentcars.avatar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.*;

class AvatarObjectTest {
  @Nested
  class Allocation {
    @Test
    @DisplayName(
        "Given a new allocation ID, when constructing its ledger entry, then retain the exact storage key")
    void key() {
      var id = UUID.randomUUID();
      assertThat(new AvatarObject(id).id()).isEqualTo(id);
    }
  }
}
