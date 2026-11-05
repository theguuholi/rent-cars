package com.github.theguuholi.rentcars.images;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

class ImageMigrationTest {
  @Nested
  class Upgrade {
    @Test
    @DisplayName(
        "Given an existing V8 avatar and account, when upgrading the shared image ledger, then preserve the key, reference and creation time")
    void existingAvatar() throws Exception {
      try (var postgres = new PostgreSQLContainer("postgres:17")) {
        postgres.start();
        var config =
            Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        config.target("8").load().migrate();
        var key = UUID.randomUUID();
        var user = UUID.randomUUID();
        try (var connection =
                java.sql.DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            var allocation =
                connection.prepareStatement(
                    "insert into avatar_objects(id,created_at) values(?,'2026-01-01T00:00:00Z')");
            var account =
                connection.prepareStatement(
                    "insert into accounts(id,email,password_hash,avatar_key) values(?,'existing@example.test','encoded',?)")) {
          allocation.setObject(1, key);
          allocation.executeUpdate();
          account.setObject(1, user);
          account.setObject(2, key);
          account.executeUpdate();
          config.target("9").load().migrate();
          try (var query = connection.createStatement();
              var result =
                  query.executeQuery(
                      "select a.avatar_key,o.created_at from accounts a join image_objects o on a.avatar_key=o.id")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getObject(1, UUID.class)).isEqualTo(key);
            assertThat(result.getTimestamp(2).toInstant())
                .isEqualTo(java.time.Instant.parse("2026-01-01T00:00:00Z"));
          }
        }
      }
    }
  }
}
