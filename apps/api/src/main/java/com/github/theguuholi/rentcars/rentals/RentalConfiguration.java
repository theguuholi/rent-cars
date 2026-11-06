package com.github.theguuholi.rentcars.rentals;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RentalConfiguration {
  @Bean
  Clock rentalClock() {
    return Clock.systemUTC();
  }
}
