import { describe, expect, test as scenario, vi } from 'vitest'
import { startTracing } from './tracing'
import { WebTracerProvider } from '@opentelemetry/sdk-trace-web'
import { FetchInstrumentation } from '@opentelemetry/instrumentation-fetch'
import { registerInstrumentations } from '@opentelemetry/instrumentation'

vi.mock('@opentelemetry/sdk-trace-web', () => ({
  WebTracerProvider: vi.fn(function () {
    return { register: vi.fn() }
  }),
}))
vi.mock('@opentelemetry/instrumentation-fetch', () => ({
  FetchInstrumentation: vi.fn(function () {
    return {}
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
    expect(WebTracerProvider).toHaveBeenCalledOnce()
    expect(FetchInstrumentation).toHaveBeenCalledWith({
      ignoreUrls: [/\/otel\/v1\/traces/],
      propagateTraceHeaderCorsUrls: [/^\/api\//],
    })
    expect(registerInstrumentations).toHaveBeenCalledOnce()
  })
})
