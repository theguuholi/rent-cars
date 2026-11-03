package com.github.theguuholi.rentcars.auth;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
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
  SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) {
    var roles = new JwtGrantedAuthoritiesConverter();
    roles.setAuthoritiesClaimName("roles");
    roles.setAuthorityPrefix("ROLE_");
    var authentication = new JwtAuthenticationConverter();
    authentication.setJwtGrantedAuthoritiesConverter(roles);

    // Browsers attach cookies automatically. Cookie-free API clients provide credentials
    // explicitly.
    return http.csrf(csrf -> csrf.ignoringRequestMatchers(request -> request.getCookies() == null))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/users", "/sessions")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/cars",
                        "/cars/{id:[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}}")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/sessions/me")
                    .authenticated()
                    .requestMatchers(
                        "/cars",
                        "/cars/**",
                        "/categories",
                        "/categories/**",
                        "/specifications",
                        "/specifications/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(r -> r.jwt(j -> j.jwtAuthenticationConverter(authentication)))
        .build();
  }
}
