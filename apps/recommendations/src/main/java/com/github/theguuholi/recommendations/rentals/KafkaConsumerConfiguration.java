package com.github.theguuholi.recommendations.rentals;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler;
import org.springframework.kafka.listener.CommonErrorHandler;

@Configuration(proxyBeanMethods = false)
class KafkaConsumerConfiguration {
  /**
   * Stop instead of skipping a failed record; restart after investigating and correcting its cause.
   */
  @Bean
  CommonErrorHandler rentalEventsErrorHandler() {
    return new CommonContainerStoppingErrorHandler();
  }
}
