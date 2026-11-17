package com.github.theguuholi.rentcars.shared;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OpenTelemetryAppenderTest {
  @Nested
  class Append {
    @Test
    void givenRealSdkWhenAppendingAnErrorThenExportsBodySeverityAndException() {
      var exporter = InMemoryLogRecordExporter.create();
      try (var provider =
          SdkLoggerProvider.builder()
              .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
              .build()) {
        var context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        var telemetry = OpenTelemetrySdk.builder().setLoggerProvider(provider).build();
        var appender = new OpenTelemetryAppender();
        appender.setContext(context);
        appender.setOpenTelemetry(telemetry);
        appender.start();
        try {
          var logger = context.getLogger("dependency-compatibility");
          logger.setLevel(Level.ERROR);
          logger.addAppender(appender);
          logger.error("Storage unavailable", new IllegalStateException("offline"));
          assertThat(exporter.getFinishedLogRecordItems())
              .singleElement()
              .satisfies(
                  log -> {
                    assertThat(log.getBodyValue()).isEqualTo(Value.of("Storage unavailable"));
                    assertThat(log.getSeverityText()).isEqualTo("ERROR");
                    assertThat(log.getAttributes().get(AttributeKey.stringKey("exception.type")))
                        .isEqualTo("java.lang.IllegalStateException");
                    assertThat(log.getAttributes().get(AttributeKey.stringKey("exception.message")))
                        .isEqualTo("offline");
                  });
        } finally {
          appender.stop();
          context.stop();
        }
      }
    }
  }
}
