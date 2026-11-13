import { WebTracerProvider } from '@opentelemetry/sdk-trace-web'
import { BatchSpanProcessor } from '@opentelemetry/sdk-trace-base'
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http'
import { FetchInstrumentation } from '@opentelemetry/instrumentation-fetch'
import { registerInstrumentations } from '@opentelemetry/instrumentation'
import { ZoneContextManager } from '@opentelemetry/context-zone'
import { resourceFromAttributes } from '@opentelemetry/resources'

export function startTracing(endpoint: string | undefined) {
  if (!endpoint) return

  const provider = new WebTracerProvider({
    resource: resourceFromAttributes({ 'service.name': 'rent-cars-web' }),
    spanProcessors: [
      new BatchSpanProcessor(new OTLPTraceExporter({ url: endpoint }), {
        scheduledDelayMillis: 500,
      }),
    ],
  })
  provider.register({ contextManager: new ZoneContextManager() })
  registerInstrumentations({
    instrumentations: [
      new FetchInstrumentation({
        ignoreUrls: [/\/otel\/v1\/traces/],
        propagateTraceHeaderCorsUrls: [/^\/api\//],
      }),
    ],
  })
}
