package com.github.theguuholi.rentcars.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

class AccountTest {
  final UUID id = UUID.randomUUID();
  final Account account = new Account("driver@example.com", "encoded-password");

  @BeforeEach
  void persistedId() {
    ReflectionTestUtils.setField(account, "id", id);
  }

  @Nested
  class Profile {
    @Test
    @DisplayName(
        "Given a new driver, when building the public profile, then it contains the identifier, email and USER role")
    void projection() {
      assertThat(account.profile()).isEqualTo(new UserProfile(id, "driver@example.com", "USER"));
    }
  }

  @Nested
  class Principal {
    @Test
    @DisplayName(
        "Given an account, when authenticating, then the principal uses its stable ID, encoded password and account role")
    void details() {
      var principal = account.principal();
      assertThat(principal.getUsername()).isEqualTo(id.toString());
      assertThat(principal.getPassword()).isEqualTo("encoded-password");
      assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }
  }
}
