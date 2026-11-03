package com.github.theguuholi.rentcars.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.theguuholi.rentcars.TestcontainersConfiguration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.SecurityFilterChain;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SecurityConfigurationTest {
  @Autowired Map<String, SecurityFilterChain> chains;

  @Nested
  class ApiSecurityFilterChain {
    @Test
    @DisplayName(
        "Given the API security configuration, when Spring starts, then apiSecurityFilterChain is the only registered security chain")
    void registersOneApiChain() {
      assertThat(chains).containsOnlyKeys("apiSecurityFilterChain");
    }
  }
}
