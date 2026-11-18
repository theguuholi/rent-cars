package com.github.theguuholi.recommendations;

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

class RecommendationsApplicationTest {
  @Nested
  class Main {
    @Test
    void givenArgumentsWhenStartingThenDelegatesToSpringBoot() {
      String[] args = {"--spring.kafka.listener.auto-startup=false"};
      try (var spring = mockStatic(SpringApplication.class)) {
        RecommendationsApplication.main(args);
        spring.verify(() -> SpringApplication.run(RecommendationsApplication.class, args));
      }
    }
  }
}
