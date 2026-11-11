package com.github.theguuholi.rentcars.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@TestConfiguration(proxyBeanMethods = false)
public class AdminRequests {
  @Bean
  MockMvcBuilderCustomizer adminCatalogRequests(
      AccessTokenService tokens, AccountRepository accounts) {
    String email = "qa-admin-" + UUID.randomUUID() + "@example.test";
    var account = new Account(email, "unused");
    account.manage(email, "ADMIN");
    UUID id = accounts.saveAndFlush(account).id();
    var admin =
        UsernamePasswordAuthenticationToken.authenticated(
            id.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    return builder ->
        builder.defaultRequest(
            get("/").header("Authorization", "Bearer " + tokens.issue(admin).accessToken()));
  }
}
