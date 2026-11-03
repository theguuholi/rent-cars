package com.github.theguuholi.rentcars.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
class AccessTokenService {
  static final String ISSUER = "rent-cars";
  static final String AUDIENCE = "rent-cars-api";
  private final JwtEncoder encoder;

  AccessTokenService(JwtEncoder encoder) {
    this.encoder = encoder;
  }

  SessionToken issue(Authentication principal) {
    Instant now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(principal.getName())
            .audience(List.of(AUDIENCE))
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofMinutes(15)))
            .claim(
                "roles",
                principal.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(role -> role.startsWith("ROLE_"))
                    .map(role -> role.substring("ROLE_".length()))
                    .toList())
            .build();
    return new SessionToken(encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue());
  }
}
