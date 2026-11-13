import { describe, expect, test as scenario, vi } from 'vitest'
import { init } from '@sentry/react'
import { startErrorReporting } from './error-reporting'

vi.mock('@sentry/react', () => ({ init: vi.fn() }))

describe('Frontend error reporting', () => {
  scenario('Given no DSN, when the app starts, then Sentry stays disabled', () => {
    startErrorReporting(undefined)
    expect(init).not.toHaveBeenCalled()
  })

  scenario('Given a DSN, when the app starts, then Sentry uses it', () => {
    startErrorReporting('https://example.invalid/1')
    expect(init).toHaveBeenCalledWith({ dsn: 'https://example.invalid/1' })
  })
})
