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
  @Autowired org.springframework.jdbc.core.simple.JdbcClient db;
  @Autowired org.springframework.transaction.support.TransactionTemplate tx;

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

  @Nested
  class ChangeAvatar {
    @Test
    @DisplayName(
        "Given a live account, when changing then clearing its avatar, then persist each pointer and report missing accounts")
    void change() {
      var account =
          accounts.saveAndFlush(new Account(UUID.randomUUID() + "@example.test", "encoded"));
      var id = account.profile().id();
      var key = UUID.randomUUID();
      db.sql("insert into image_objects(id) values(?)").param(key).update();
      tx.executeWithoutResult(_ -> assertThat(accounts.changeAvatar(id, key)).isOne());
      tx.executeWithoutResult(
          _ -> assertThat(accounts.findWithAvatar(id).orElseThrow().avatarKey()).isEqualTo(key));
      tx.executeWithoutResult(_ -> assertThat(accounts.changeAvatar(id, null)).isOne());
      tx.executeWithoutResult(_ -> assertThat(accounts.findWithAvatar(id)).isEmpty());
      tx.executeWithoutResult(
          _ -> assertThat(accounts.changeAvatar(UUID.randomUUID(), key)).isZero());
    }
  }

  @Nested
  class FindWithAvatar {
    @Test
    @DisplayName(
        "Given an unknown account, when looking up its avatar under a read lock, then return empty")
    void missing() {
      tx.executeWithoutResult(
          _ -> assertThat(accounts.findWithAvatar(UUID.randomUUID())).isEmpty());
    }
  }
}
