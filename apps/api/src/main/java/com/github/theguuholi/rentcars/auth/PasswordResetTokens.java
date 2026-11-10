package com.github.theguuholi.rentcars.auth;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PasswordResetTokens {
  private final JdbcClient db;
  private final SecureRandom random = new SecureRandom();

  PasswordResetTokens(JdbcClient db) {
    this.db = db;
  }

  String issue(UUID accountId) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    db.sql("delete from password_reset_tokens where expires_at <= clock_timestamp()").update();
    db.sql(
            """
            insert into password_reset_tokens(digest,account_id,expires_at) values(?,?,?)
            on conflict (account_id) do update set digest=excluded.digest, expires_at=excluded.expires_at
            """)
        .params(
            DigestUtils.sha256(secret),
            accountId,
            Timestamp.from(Instant.now().plus(Duration.ofMinutes(30))))
        .update();
    return secret;
  }

  Optional<UUID> consume(String secret) {
    return db.sql(
            "delete from password_reset_tokens where digest=? and expires_at > clock_timestamp() returning account_id")
        .param(DigestUtils.sha256(secret))
        .query(UUID.class)
        .optional();
  }

  void invalidate(UUID accountId) {
    db.sql("delete from password_reset_tokens where account_id=?").param(accountId).update();
  }

  void revokeSessions(UUID accountId) {
    db.sql("delete from refresh_sessions where account_id=?").param(accountId).update();
  }
}
