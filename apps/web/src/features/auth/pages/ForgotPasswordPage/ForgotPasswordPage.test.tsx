import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, test as scenario, vi } from 'vitest'
import { openCatalogRoute } from '../../../../test/open-catalog-route'
import { requestPasswordReset } from '../../auth-api'

vi.mock('../../auth-api')

describe('ForgotPasswordPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
    vi.unstubAllGlobals()
  })

  scenario(
    'Given the login page, when a visitor requests recovery, then the generic confirmation replaces the form',
    async () => {
      const history = openCatalogRoute('/login', undefined, null)
      fireEvent.click(await screen.findByRole('link', { name: 'Forgot password?' }))
      expect(history.location.pathname).toBe('/forgot-password')
      await waitFor(() => expect(document.title).toBe('Forgot password | Rent Cars'))
      const email = await screen.findByLabelText('Email')
      expect(email).toHaveAttribute('type', 'email')
      expect(email).toBeRequired()
      fireEvent.change(email, { target: { value: ' Driver@Example.Test ' } })
      let finish!: () => void
      vi.mocked(requestPasswordReset).mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finish = resolve
          }),
      )
      fireEvent.submit(screen.getByRole('button', { name: 'Send reset link' }).closest('form')!)
      await waitFor(() =>
        expect(screen.getByRole('button', { name: 'Send reset link' })).toBeDisabled(),
      )
      expect(requestPasswordReset).toHaveBeenCalledExactlyOnceWith('driver@example.test')
      await act(async () => finish())
      expect(await screen.findByRole('status')).toHaveTextContent(
        'If the account exists, a reset link is on its way.',
      )
      expect(screen.queryByLabelText('Email')).not.toBeInTheDocument()
      fireEvent.click(screen.getByRole('link', { name: 'Back to sign in' }))
      expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeVisible()
    },
  )

  scenario.each([new Error('SMTP unavailable'), 'offline'])(
    'Given a request failure, when submitting, then the error is visible and the email stays editable',
    async (failure) => {
      vi.mocked(requestPasswordReset).mockRejectedValueOnce(failure)
      openCatalogRoute('/forgot-password', undefined, null)
      const email = await screen.findByLabelText('Email')
      fireEvent.change(email, { target: { value: 'driver@example.test' } })
      fireEvent.submit(screen.getByRole('button', { name: 'Send reset link' }).closest('form')!)
      expect(await screen.findByRole('alert')).toHaveTextContent(
        failure instanceof Error ? failure.message : 'Could not request a reset link.',
      )
      expect(email).toHaveValue('driver@example.test')
    },
  )
})
