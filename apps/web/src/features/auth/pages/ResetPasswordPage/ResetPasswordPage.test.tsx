import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, test as scenario, vi } from 'vitest'
import { openCatalogRoute } from '../../../../test/open-catalog-route'
import { resetPassword } from '../../auth-api'

vi.mock('../../auth-api')

async function fill(password: string, confirmation: string) {
  fireEvent.change(await screen.findByLabelText('New password', { exact: true }), {
    target: { value: password },
  })
  fireEvent.change(screen.getByLabelText('Confirm new password'), {
    target: { value: confirmation },
  })
}

function submit() {
  fireEvent.submit(screen.getByRole('button', { name: 'Reset password' }).closest('form')!)
}

describe('ResetPasswordPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
    vi.unstubAllGlobals()
  })

  scenario(
    'Given a link without token, when opened, then explain how to request another link',
    async () => {
      openCatalogRoute('/reset-password', undefined, null)
      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Reset link is invalid or expired.',
      )
      expect(screen.queryByRole('button', { name: 'Reset password' })).not.toBeInTheDocument()
      fireEvent.click(screen.getByRole('link', { name: 'Request a new link' }))
      expect(await screen.findByRole('heading', { name: 'Forgot password' })).toBeVisible()
    },
  )

  scenario(
    'Given a link with token, when matching passwords are submitted, then show success and sign-in',
    async () => {
      openCatalogRoute('/reset-password?token=one-time-token', undefined, null)
      await fill('replacement-password-123', 'replacement-password-123')
      let finish!: () => void
      vi.mocked(resetPassword).mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finish = resolve
          }),
      )
      submit()
      await waitFor(() =>
        expect(screen.getByRole('button', { name: 'Reset password' })).toBeDisabled(),
      )
      expect(resetPassword).toHaveBeenCalledExactlyOnceWith({
        token: 'one-time-token',
        password: 'replacement-password-123',
        passwordConfirmation: 'replacement-password-123',
      })
      await act(async () => finish())
      expect(await screen.findByRole('status')).toHaveTextContent(
        'Password updated. You can sign in now.',
      )
      fireEvent.click(within(screen.getByRole('main')).getByRole('link', { name: 'Sign in' }))
      expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeVisible()
    },
  )

  scenario(
    'Given mismatched passwords, when submitted, then show a field error without an API call',
    async () => {
      openCatalogRoute('/reset-password?token=one-time-token', undefined, null)
      await fill('replacement-password-123', 'different-password-123')
      submit()
      expect(await screen.findByRole('alert')).toHaveTextContent('Passwords do not match.')
      expect(screen.getByLabelText(/Confirm new password/)).toHaveAccessibleDescription(
        'Passwords do not match.',
      )
      expect(resetPassword).not.toHaveBeenCalled()
    },
  )

  scenario(
    'Given a password that is too short, when submitted, then show its rule before calling the API',
    async () => {
      openCatalogRoute('/reset-password?token=one-time-token', undefined, null)
      await fill('short', 'short')
      submit()
      expect(await screen.findByRole('alert')).toHaveTextContent('at least 12')
      expect(screen.getAllByDisplayValue('short')[0]).toHaveAccessibleDescription(/at least 12/)
      expect(resetPassword).not.toHaveBeenCalled()
    },
  )

  scenario.each([new Error('Reset link is invalid or expired.'), 'offline'])(
    'Given the API rejects a reset, when submitting, then keep the draft and show the error',
    async (failure) => {
      vi.mocked(resetPassword).mockRejectedValueOnce(failure)
      openCatalogRoute('/reset-password?token=one-time-token', undefined, null)
      await fill('replacement-password-123', 'replacement-password-123')
      submit()
      expect(await screen.findByRole('alert')).toHaveTextContent(
        failure instanceof Error ? failure.message : 'Could not reset your password.',
      )
      expect(screen.getByLabelText('New password', { exact: true })).toHaveValue(
        'replacement-password-123',
      )
      expect(screen.getByRole('link', { name: 'Request a new link' })).toBeVisible()
    },
  )
})
