import { describe, expect, test as scenario, vi } from 'vitest'
import { initializeObservability } from './initialize'
import { startTracing } from './tracing'
import { startErrorReporting } from './error-reporting'

vi.mock('./tracing', () => ({ startTracing: vi.fn() }))
vi.mock('./error-reporting', () => ({ startErrorReporting: vi.fn() }))

describe('Observability startup', () => {
  scenario(
    'Given no endpoints, when the app starts, then optional telemetry stays off',
    async () => {
      await initializeObservability(undefined, undefined)
      expect(startTracing).not.toHaveBeenCalled()
      expect(startErrorReporting).not.toHaveBeenCalled()
    },
  )

  scenario(
    'Given both endpoints, when the app starts, then both reporters are configured',
    async () => {
      await initializeObservability('https://example.invalid/1', '/otel/v1/traces')
      expect(startErrorReporting).toHaveBeenCalledWith('https://example.invalid/1')
      expect(startTracing).toHaveBeenCalledWith('/otel/v1/traces')
    },
  )
})
