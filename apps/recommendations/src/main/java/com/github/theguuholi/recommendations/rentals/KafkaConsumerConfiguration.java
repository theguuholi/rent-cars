package com.github.theguuholi.recommendations.rentals;

import jakarta.validation.ConstraintViolationException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import tools.jackson.core.JacksonException;

@Configuration(proxyBeanMethods = false)
class KafkaConsumerConfiguration {
  @Bean
  ExponentialBackOffWithMaxRetries rentalRetryBackOff(
      @Value("${rent.messaging.retry.max-retries}") int retries,
      @Value("${rent.messaging.retry.initial-interval-ms}") long interval) {
    var backOff = new ExponentialBackOffWithMaxRetries(retries);
    backOff.setInitialInterval(interval);
    backOff.setMultiplier(2.0);
    return backOff;
  }

  @Bean
  DeadLetterPublishingRecoverer rentalDeadLetterRecoverer(
      KafkaTemplate<String, String> kafka,
      @Value("${rent.messaging.dead-letter-topic}") String topic) {
    // AT-LEAST-ONCE: Spring Kafka 4.1 waits for DLT confirmation and throws on send failure
    // by default. Failure-path tests guard this guarantee when upgrading Spring Kafka.
    // A crash before the source offset commit may publish the same event again.
    return new DeadLetterPublishingRecoverer(
        kafka, (record, exception) -> new TopicPartition(topic, record.partition()));
  }

  @Bean
  DefaultErrorHandler rentalEventsErrorHandler(
      DeadLetterPublishingRecoverer recoverer,
      ExponentialBackOffWithMaxRetries backOff,
      RentalEventRecoveryObserver observer) {
    var handler = new DefaultErrorHandler(recoverer, backOff);
    handler.setRetryListeners(observer);
    handler.addNotRetryableExceptions(
        JacksonException.class, ConstraintViolationException.class, IllegalArgumentException.class);
    return handler;
  }
}
