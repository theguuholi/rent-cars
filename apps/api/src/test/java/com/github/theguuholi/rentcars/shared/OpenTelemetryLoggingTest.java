package com.github.theguuholi.rentcars.shared;

import static org.mockito.Mockito.mockStatic;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OpenTelemetryLoggingTest {
  @Nested
  class AfterPropertiesSet {
    @Test
    void givenOpenTelemetryWhenInitializedThenInstallsTheLogbackBridge() {
      var telemetry = OpenTelemetry.noop();
      try (var appender = mockStatic(OpenTelemetryAppender.class)) {
        new OpenTelemetryLogging(telemetry).afterPropertiesSet();
        appender.verify(() -> OpenTelemetryAppender.install(telemetry));
      }
    }
  }
}
