import { render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, test as scenario, vi } from 'vitest'
import { captureException } from '@sentry/react'
import { AppErrorBoundary } from './AppErrorBoundary'

vi.mock('@sentry/react', () => ({ captureException: vi.fn() }))

function BrokenPage(): never {
  throw new Error('Render failed')
}

describe('Application error boundary', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    vi.clearAllMocks()
  })

  scenario('Given a healthy page, when it renders, then the content stays visible', () => {
    render(<AppErrorBoundary>Catalog</AppErrorBoundary>)
    expect(screen.getByText('Catalog')).toBeVisible()
  })

  scenario(
    'Given no Sentry DSN, when rendering fails, then the visitor sees a recovery message',
    () => {
      const errorLog = vi.spyOn(console, 'error').mockImplementation(() => {})
      try {
        render(
          <AppErrorBoundary>
            <BrokenPage />
          </AppErrorBoundary>,
        )
        expect(screen.getByRole('alert')).toHaveTextContent('Reload the page')
        expect(captureException).not.toHaveBeenCalled()
      } finally {
        errorLog.mockRestore()
      }
    },
  )

  scenario(
    'Given Sentry is configured, when rendering fails, then the error is reported',
    async () => {
      vi.stubEnv('VITE_SENTRY_DSN', 'https://example.invalid/1')
      const errorLog = vi.spyOn(console, 'error').mockImplementation(() => {})
      try {
        render(
          <AppErrorBoundary>
            <BrokenPage />
          </AppErrorBoundary>,
        )
        await waitFor(() =>
          expect(captureException).toHaveBeenCalledWith(
            expect.objectContaining({ message: 'Render failed' }),
            expect.objectContaining({
              extra: expect.objectContaining({ componentStack: expect.any(String) }),
            }),
          ),
        )
      } finally {
        errorLog.mockRestore()
      }
    },
  )
})
