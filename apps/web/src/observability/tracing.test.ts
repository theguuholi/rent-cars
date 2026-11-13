import { describe, expect, test as scenario, vi } from 'vitest'
import { startTracing } from './tracing'
import { WebTracerProvider } from '@opentelemetry/sdk-trace-web'
import { FetchInstrumentation } from '@opentelemetry/instrumentation-fetch'
import { registerInstrumentations } from '@opentelemetry/instrumentation'
import { BatchSpanProcessor } from '@opentelemetry/sdk-trace-base'
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http'
import { ZoneContextManager } from '@opentelemetry/context-zone'
import { resourceFromAttributes } from '@opentelemetry/resources'

const tracing = vi.hoisted(() => ({
  register: vi.fn(),
  exporter: { type: 'exporter' },
  processor: { type: 'processor' },
  contextManager: { type: 'context-manager' },
  instrumentation: { type: 'fetch' },
}))

vi.mock('@opentelemetry/sdk-trace-web', () => ({
  WebTracerProvider: vi.fn(function () {
    return { register: tracing.register }
  }),
}))
vi.mock('@opentelemetry/sdk-trace-base', () => ({
  BatchSpanProcessor: vi.fn(function () {
    return tracing.processor
  }),
}))
vi.mock('@opentelemetry/exporter-trace-otlp-http', () => ({
  OTLPTraceExporter: vi.fn(function () {
    return tracing.exporter
  }),
}))
vi.mock('@opentelemetry/context-zone', () => ({
  ZoneContextManager: vi.fn(function () {
    return tracing.contextManager
  }),
}))
vi.mock('@opentelemetry/resources', () => ({
  resourceFromAttributes: vi.fn((attributes) => attributes),
}))
vi.mock('@opentelemetry/instrumentation-fetch', () => ({
  FetchInstrumentation: vi.fn(function () {
    return tracing.instrumentation
  }),
}))
vi.mock('@opentelemetry/instrumentation', () => ({ registerInstrumentations: vi.fn() }))

describe('Browser tracing', () => {
  scenario('Given no collector URL, when the app starts, then tracing stays disabled', () => {
    startTracing(undefined)
    expect(WebTracerProvider).not.toHaveBeenCalled()
  })

  scenario('Given a collector URL, when the app starts, then API fetches are traced', () => {
    startTracing('/otel/v1/traces')
    expect(resourceFromAttributes).toHaveBeenCalledWith({ 'service.name': 'rent-cars-web' })
    expect(OTLPTraceExporter).toHaveBeenCalledWith({ url: '/otel/v1/traces' })
    expect(BatchSpanProcessor).toHaveBeenCalledWith(tracing.exporter, {
      scheduledDelayMillis: 500,
    })
    expect(WebTracerProvider).toHaveBeenCalledWith({
      resource: { 'service.name': 'rent-cars-web' },
      spanProcessors: [tracing.processor],
    })
    expect(ZoneContextManager).toHaveBeenCalledOnce()
    expect(tracing.register).toHaveBeenCalledWith({ contextManager: tracing.contextManager })
    expect(FetchInstrumentation).toHaveBeenCalledWith({
      ignoreUrls: [/\/otel\/v1\/traces/],
      propagateTraceHeaderCorsUrls: [/^\/api\//],
    })
    expect(registerInstrumentations).toHaveBeenCalledWith({
      instrumentations: [tracing.instrumentation],
    })
  })
})
