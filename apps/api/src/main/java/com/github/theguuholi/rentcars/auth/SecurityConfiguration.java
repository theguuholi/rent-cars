package com.github.theguuholi.rentcars.auth;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

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

    var paths = PathPatternRequestMatcher.withDefaults();
    var catalog =
        new OrRequestMatcher(
            paths.matcher("/cars/**"),
            paths.matcher("/categories/**"),
            paths.matcher("/specifications/**"));
    var cookieEndpoints =
        new OrRequestMatcher(
            paths.matcher(HttpMethod.POST, "/sessions/refresh"),
            paths.matcher(HttpMethod.DELETE, "/sessions/current"));
    var bearer =
        new org.springframework.security.oauth2.server.resource.web.authentication
            .BearerTokenAuthenticationConverter();
    var forbidden = new BearerTokenAccessDeniedHandler();
    return http.csrf(CsrfConfigurer::spa)
        .exceptionHandling(
            errors ->
                errors.defaultAccessDeniedHandlerFor(
                    (request, response, denied) -> {
                      // A catalog write without a bearer token is an authentication failure for API
                      // clients.
                      if (denied instanceof CsrfException) {
                        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                        response.setStatus(HttpStatus.UNAUTHORIZED.value());
                      } else {
                        forbidden.handle(request, response, denied);
                      }
                    },
                    catalog))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(cookieEndpoints)
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
                        "/csrf",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/sessions/me")
                    .authenticated()
                    .requestMatchers(catalog)
                    .hasRole("ADMIN")
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(
            r ->
                r.authenticationConverter(
                        request ->
                            cookieEndpoints.matches(request) ? null : bearer.convert(request))
                    .jwt(j -> j.jwtAuthenticationConverter(authentication)))
        .build();
  }
}
