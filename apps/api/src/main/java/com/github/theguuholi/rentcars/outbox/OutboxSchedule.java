package com.github.theguuholi.rentcars.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "rent.messaging.enabled", havingValue = "true")
class OutboxSchedule {
  private final OutboxPublisher publisher;

  OutboxSchedule(OutboxPublisher publisher) {
    this.publisher = publisher;
  }

  @Scheduled(fixedDelayString = "${rent.messaging.poll-interval:1s}")
  void publishBatch() {
    for (int i = 0; i < 20; i++) {
      if (!publisher.publishNext()) break;
    }
  }
}
