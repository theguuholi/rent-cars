package com.github.theguuholi.recommendations.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;

class RecommendationSecurityTest {
  @Nested
  class RecommendationSecurityFilterChain {
    @Test
    void givenConfiguredSecurityWhenCreatingBeanThenRegistersTheBuiltFilterChain()
        throws Exception {
      var http = mock(HttpSecurity.class, RETURNS_SELF);
      var chain = mock(DefaultSecurityFilterChain.class);
      when(http.build()).thenReturn(chain);

      assertThat(new RecommendationSecurity().recommendationSecurityFilterChain(http))
          .isSameAs(chain);
    }
  }
}
