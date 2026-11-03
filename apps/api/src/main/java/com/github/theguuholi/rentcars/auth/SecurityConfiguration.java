package com.github.theguuholi.rentcars.auth;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  AuthenticationManager authenticationManager(
      AccountRepository accounts, PasswordEncoder passwords) {
    var provider =
        new DaoAuthenticationProvider(
            email ->
                accounts
                    .findByEmail(email)
                    .map(Account::principal)
                    .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials")));
    provider.setPasswordEncoder(passwords);
    return new ProviderManager(provider);
  }

  @Bean
  @Profile({"dev", "test"})
  KeyPair localSigningKeys() throws GeneralSecurityException {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  @Bean
  @Profile("!dev & !test")
  KeyPair signingKeys(
      @Value("${rent.auth.public-key}") Resource publicKey,
      @Value("${rent.auth.private-key}") Resource privateKey)
      throws IOException {
    try (var pub = publicKey.getInputStream();
        var priv = privateKey.getInputStream()) {
      return new KeyPair(
          RsaKeyConverters.x509().convert(pub), RsaKeyConverters.pkcs8().convert(priv));
    }
  }

  @Bean
  JwtEncoder jwtEncoder(KeyPair keys) {
    return NimbusJwtEncoder.withKeyPair(
            (RSAPublicKey) keys.getPublic(), (RSAPrivateKey) keys.getPrivate())
        .build();
  }

  @Bean
  JwtDecoder jwtDecoder(KeyPair keys) {
    var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
    var uuid = Pattern.compile("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(AccessTokenService.ISSUER),
            new JwtClaimValidator<List<String>>(
                "aud", aud -> aud.contains(AccessTokenService.AUDIENCE)),
            new JwtClaimValidator<>("exp", Objects::nonNull),
            new JwtClaimValidator<String>("sub", uuid.asMatchPredicate())));
    return decoder;
  }

  @Bean
  SecurityFilterChain sessionSecurity(HttpSecurity http) throws Exception {
    return http.securityMatcher("/sessions/me")
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .oauth2ResourceServer(r -> r.jwt(Customizer.withDefaults()))
        .build();
  }
}
