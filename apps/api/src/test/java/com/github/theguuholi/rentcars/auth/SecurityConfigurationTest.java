package com.github.theguuholi.rentcars.auth;

import static org.assertj.core.api.Assertions.*;

import com.github.theguuholi.rentcars.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Instant;
import java.util.*;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityConfigurationTest {
  @Autowired SecurityConfiguration config;
  @Autowired KeyPair keys;

  @Autowired MockMvc mvc;

  @Nested
  class SigningKeys {
    @Test
    @DisplayName(
        "Given externally provisioned RSA PEM files, when loading production keys, then the configured keypair is preserved")
    void external() throws Exception {
      var pair =
          config.signingKeys(
              pem("PUBLIC", keys.getPublic().getEncoded()),
              pem("PRIVATE", keys.getPrivate().getEncoded()));
      assertThat(pair.getPublic()).isEqualTo(keys.getPublic());
      assertThat(pair.getPrivate()).isEqualTo(keys.getPrivate());
    }

    ByteArrayResource pem(String type, byte[] bytes) {
      return new ByteArrayResource(
          ("-----BEGIN "
                  + type
                  + " KEY-----\n"
                  + Base64.getMimeEncoder().encodeToString(bytes)
                  + "\n-----END "
                  + type
                  + " KEY-----\n")
              .getBytes(StandardCharsets.US_ASCII));
    }
  }

  @Nested
  class Decode {
    @ParameterizedTest(
        name = "Given a token with {0}, when validating it, then the resource server rejects it")
    @ValueSource(
        strings = {
          "expired",
          "wrong issuer",
          "wrong audience",
          "missing audience",
          "missing expiration",
          "invalid subject",
          "missing subject",
          "untrusted signature",
          "modified payload",
          "future not-before"
        })
    void rejected(String scenario) throws Exception {
      var now = Instant.now();
      var claims = new HashMap<String, Object>();
      claims.put("iss", "rent-cars");
      claims.put("aud", List.of("rent-cars-api"));
      claims.put("sub", UUID.randomUUID().toString());
      claims.put("iat", now.minusSeconds(600));
      claims.put("exp", now.plusSeconds(600));
      switch (scenario) {
        case "expired" -> claims.put("exp", now.minusSeconds(300));
        case "wrong issuer" -> claims.put("iss", "another-issuer");
        case "wrong audience" -> claims.put("aud", List.of("other-api"));
        case "missing audience" -> claims.remove("aud");
        case "missing expiration" -> claims.remove("exp");
        case "invalid subject" -> claims.put("sub", "invalid");
        case "missing subject" -> claims.remove("sub");
        case "future not-before" -> claims.put("nbf", now.plusSeconds(300));
        default -> {}
      }
      var signingKey = scenario.equals("untrusted signature") ? config.localSigningKeys() : keys;
      String signed =
          config
              .jwtEncoder(signingKey)
              .encode(
                  JwtEncoderParameters.from(
                      JwtClaimsSet.builder().claims(c -> c.putAll(claims)).build()))
              .getTokenValue();
      if (scenario.equals("modified payload")) {
        var parts = signed.split("\\.");
        var payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        parts[1] =
            Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                    payload
                        .replace("rent-cars-api", "rent-cars-api-changed")
                        .getBytes(StandardCharsets.UTF_8));
        signed = String.join(".", parts);
      }
      String token = signed;
      mvc.perform(
              MockMvcRequestBuilders.get("/sessions/me").header("Authorization", "Bearer " + token))
          .andExpect(MockMvcResultMatchers.status().isUnauthorized())
          .andExpect(
              MockMvcResultMatchers.header()
                  .string("WWW-Authenticate", Matchers.containsString("invalid_token")));
      var decoder = config.jwtDecoder(keys);
      assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
  }
}
