package com.github.theguuholi.rentcars.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.theguuholi.rentcars.TestcontainersConfiguration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityConfigurationTest {
  @Autowired Map<String, SecurityFilterChain> chains;
  @Autowired MockMvc mvc;

  @Nested
  class ApiSecurityFilterChain {
    @Test
    @DisplayName(
        "Given the API security configuration, when Spring starts, then apiSecurityFilterChain is the only registered security chain")
    void registersOneApiChain() {
      assertThat(chains).containsOnlyKeys("apiSecurityFilterChain");
    }

    @Test
    @DisplayName("Given an anonymous visitor, when requesting health, then the probe is available")
    void exposesHealthWithoutCredentials() throws Exception {
      mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
  }
}
