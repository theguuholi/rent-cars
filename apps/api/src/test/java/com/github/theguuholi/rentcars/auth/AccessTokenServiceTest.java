package com.github.theguuholi.rentcars.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AccessTokenServiceTest {
  @Nested
  class Issue {
    @Test
    @DisplayName(
        "Given an admin authenticated with a password factor, when issuing a token, then only application roles become role claims")
    void roles() throws Exception {
      var config = new JwtConfiguration();
      var keys = config.localSigningKeys();
      String id = UUID.randomUUID().toString();
      var admin =
          UsernamePasswordAuthenticationToken.authenticated(
              id,
              null,
              List.of(
                  new SimpleGrantedAuthority("ROLE_ADMIN"),
                  new SimpleGrantedAuthority("FACTOR_PASSWORD")));
      var token = new AccessTokenService(config.jwtEncoder(keys)).issue(admin);
      var jwt = config.jwtDecoder(keys).decode(token.accessToken());
      assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
      assertThat(jwt.getSubject()).isEqualTo(id);
    }
  }
}
