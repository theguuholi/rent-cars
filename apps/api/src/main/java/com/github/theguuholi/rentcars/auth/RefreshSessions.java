package com.github.theguuholi.rentcars.auth;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RefreshSessions {
  static final String COOKIE = "rent_refresh";

  record Grant(SessionToken access, ResponseCookie cookie) {}

  private record Family(UUID id, UUID account, Instant expiry) {}

  private final SecureRandom random = new SecureRandom();
  private final JdbcClient db;
  private final AccountRepository accounts;
  private final AccessTokenService tokens;
  private final boolean secure;
  private final Duration lifetime;

  RefreshSessions(
      JdbcClient db,
      AccountRepository accounts,
      AccessTokenService tokens,
      @Value("${rent.auth.secure-cookie:true}") boolean secure,
      @Value("${rent.auth.refresh-lifetime:P7D}") Duration lifetime) {
    this.db = db;
    this.accounts = accounts;
    this.tokens = tokens;
    this.secure = secure;
    this.lifetime = lifetime;
  }

  @Transactional
  Grant open(Authentication principal) {
    db.sql("delete from refresh_sessions where expires_at <= now()").update();
    var family =
        new Family(
            UUID.randomUUID(), UUID.fromString(principal.getName()), Instant.now().plus(lifetime));
    db.sql("insert into refresh_sessions(id,account_id,expires_at) values(?,?,?)")
        .params(family.id(), family.account(), Timestamp.from(family.expiry()))
        .update();
    return issue(family, principal);
  }

  @Transactional
  Optional<Grant> rotate(String secret) {
    byte[] digest = sha256(secret);
    var locked =
        db.sql(
                """
        select s.id,s.account_id,s.expires_at from refresh_sessions s
        join refresh_tokens t on t.session_id=s.id
        where t.digest=? and s.expires_at > clock_timestamp() for update of s
        """)
            .param(digest)
            .query(
                (rs, n) ->
                    new Family(
                        rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class),
                        rs.getTimestamp(3).toInstant()))
            .optional();
    return locked.flatMap(
        family -> {
          // A separate statement sees the winning transaction after waiting for the family lock.
          if (db.sql("update refresh_tokens set consumed=true where digest=? and not consumed")
                  .param(digest)
                  .update()
              == 0) {
            revoke(secret);
            return Optional.empty();
          }
          var principal = accounts.findById(family.account()).orElseThrow().principal();
          return Optional.of(
              issue(
                  family,
                  UsernamePasswordAuthenticationToken.authenticated(
                      principal, null, principal.getAuthorities())));
        });
  }

  @Transactional
  void revoke(String secret) {
    db.sql(
            "delete from refresh_sessions where id=(select session_id from refresh_tokens where digest=?)")
        .param(sha256(secret))
        .update();
  }

  ResponseCookie cookie(String secret, Duration age) {
    return ResponseCookie.from(COOKIE, secret)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Lax")
        .path("/")
        .maxAge(age)
        .build();
  }

  private static byte[] sha256(String secret) {
    return org.apache.commons.codec.digest.DigestUtils.sha256(secret);
  }

  private Grant issue(Family family, Authentication principal) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    var secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    db.sql("insert into refresh_tokens(digest,session_id) values(?,?)")
        .params(sha256(secret), family.id())
        .update();
    return new Grant(
        tokens.issue(principal), cookie(secret, Duration.between(Instant.now(), family.expiry())));
  }
}
