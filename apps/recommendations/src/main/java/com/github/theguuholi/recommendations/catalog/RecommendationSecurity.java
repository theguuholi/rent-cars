package com.github.theguuholi.recommendations.catalog;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@SecurityScheme(name = "serviceCredentials", type = SecuritySchemeType.HTTP, scheme = "basic")
class RecommendationSecurity {
  @Bean
  SecurityFilterChain recommendationSecurityFilterChain(HttpSecurity http) throws Exception {
    // This read-only service has no form login or state-changing HTTP operations.
    return http.sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(requests ->
            requests
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                .permitAll()
                .requestMatchers(
                    HttpMethod.GET,
                    "/recommendations/cars",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html")
                .authenticated()
                .anyRequest()
                .denyAll())
        .httpBasic(Customizer.withDefaults())
        .build();
  }
}
