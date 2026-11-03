package com.github.theguuholi.rentcars.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.theguuholi.rentcars.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountRepositoryTest {
  @Autowired AccountRepository accounts;

  @Nested
  class FindByEmail {
    @Test
    @DisplayName(
        "Given competing signups, when both insert the same email, then one commits and lookup returns the winning profile")
    void concurrentSignup() throws Exception {
      String email = UUID.randomUUID() + "@example.com";
      var ready = new CyclicBarrier(2);
      Callable<Boolean> signup =
          () -> {
            ready.await(5, TimeUnit.SECONDS);
            try {
              accounts.saveAndFlush(new Account(email, "encoded"));
              return true;
            } catch (DataIntegrityViolationException _) {
              return false;
            }
          };
      try (var pool = Executors.newFixedThreadPool(2)) {
        var results = pool.invokeAll(List.of(signup, signup), 15, TimeUnit.SECONDS);
        assertThat(List.of(results.getFirst().get(), results.getLast().get()))
            .containsExactlyInAnyOrder(true, false);
      }
      assertThat(accounts.findByEmail(email).orElseThrow().profile().email()).isEqualTo(email);
      assertThat(accounts.findByEmail("missing-" + email)).isEmpty();
    }
  }
}
