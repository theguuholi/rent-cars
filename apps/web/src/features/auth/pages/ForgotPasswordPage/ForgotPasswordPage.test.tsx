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
      expect(email).toHaveValue('')
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
      fireEvent.change(email, { target: { value: '\u00a0Driver@Example.Test\u00a0' } })
      let finish!: () => void
      vi.mocked(requestPasswordReset).mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finish = resolve
          }),
      )
      expect(
        fireEvent.submit(screen.getByRole('button', { name: 'Send reset link' }).closest('form')!),
      ).toBe(false)
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
      expect(
        fireEvent.submit(screen.getByRole('button', { name: 'Send reset link' }).closest('form')!),
      ).toBe(false)
      expect(await screen.findByRole('alert')).toHaveTextContent(
        failure instanceof Error ? failure.message : 'Could not request a reset link.',
      )
      expect(email).toHaveValue('driver@example.test')
    },
  )
})

scenario(
  'Given a failed reset request, when retrying, then clear the old error while preserving the email',
  async () => {
    vi.mocked(requestPasswordReset)
      .mockRejectedValueOnce(new Error('Offline'))
      .mockReturnValueOnce(new Promise(() => {}))
    openCatalogRoute('/forgot-password', undefined, null)
    fireEvent.change(await screen.findByLabelText('Email'), {
      target: { value: 'driver@example.test' },
    })
    const form = screen.getByRole('button', { name: 'Send reset link' }).closest('form')!
    fireEvent.submit(form)
    expect(await screen.findByRole('alert')).toHaveTextContent('Offline')
    fireEvent.submit(form)
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Send reset link' })).toBeDisabled(),
    )
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveValue('driver@example.test')
  },
)

scenario(
  'Given an untouched recovery form, when submitted programmatically, then use the empty string draft instead of failing on an undefined email',
  async () => {
    vi.mocked(requestPasswordReset).mockReset().mockRejectedValueOnce(new Error('Enter an email'))
    openCatalogRoute('/forgot-password', undefined, null)
    const button = await screen.findByRole('button', { name: 'Send reset link' })
    fireEvent.submit(button.closest('form')!)
    await waitFor(() => expect(requestPasswordReset).toHaveBeenCalledExactlyOnceWith(''))
    expect(await screen.findByRole('alert')).toHaveTextContent('Enter an email')
  },
)
