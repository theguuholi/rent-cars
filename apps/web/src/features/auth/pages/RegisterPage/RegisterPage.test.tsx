import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, test as scenario, vi } from 'vitest'
import { openCatalogRoute } from '../../../../test/open-catalog-route'
import { registerAccount } from '../../auth-api'
import { session } from '../../session'
vi.mock('../../auth-api')

describe('RegisterPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
    vi.unstubAllGlobals()
  })
  scenario(
    'Given a visitor on sign-in, when opening registration and submitting valid credentials, then prevent duplicate clicks and offer sign-in after creation',
    async () => {
      const history = openCatalogRoute('/login', undefined, null)
      const link = await screen.findByRole('link', { name: 'Create an account' })
      expect(link).toHaveAttribute('href', '/register')
      fireEvent.click(link)
      await screen.findByRole('heading', { name: 'Create account' })
      const email = await screen.findByLabelText('Email')
      const password = screen.getByLabelText('Password')
      await waitFor(() => expect(document.title).toBe('Create account | Rent Cars'))
      expect(history.location.pathname).toBe('/register')
      expect(screen.getByText('Register as a driver.')).toBeVisible()
      expect(screen.getByText('Account')).toBeVisible()
      expect(screen.getByRole('heading', { name: 'Create account' })).toBeVisible()
      expect(screen.queryByRole('alert')).toBeNull()
      expect(email).toHaveValue('')
      expect(password).toHaveValue('')
      expect(email).toHaveAttribute('type', 'email')
      expect(email).toHaveAttribute('autocomplete', 'username')
      expect(email).toHaveAttribute('maxlength', '254')
      expect(email).toBeRequired()
      expect(password).toBeRequired()
      expect(password).toHaveAttribute('type', 'password')
      expect(password).toHaveAttribute('autocomplete', 'new-password')
      expect(password).toHaveAttribute('minlength', '12')
      expect(password).toHaveAttribute('maxlength', '72')
      expect(password).toHaveAccessibleDescription(
        'Use at least 12 characters and at most 72 UTF-8 bytes.',
      )
      const value = 'é'.repeat(36)
      fireEvent.change(email, { target: { value: 'driver@example.test' } })
      fireEvent.change(password, { target: { value } })
      let finish!: () => void
      vi.mocked(registerAccount).mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finish = resolve
          }),
      )
      const button = screen.getByRole('button', { name: 'Create account' })
      expect(fireEvent.submit(button.closest('form')!)).toBe(false)
      await waitFor(() => expect(button).toBeDisabled())
      expect(registerAccount).toHaveBeenCalledExactlyOnceWith('driver@example.test', value)
      await act(async () => finish())
      expect(await screen.findByRole('status')).toHaveTextContent(
        'Your account is ready. Sign in to continue.',
      )
      expect(screen.queryByLabelText('Password')).toBeNull()
      expect(session.read()).toBeNull()
      fireEvent.click(screen.getByRole('link', { name: 'Back to sign in' }))
      expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeVisible()
    },
  )
  scenario.each(['é'.repeat(37), ' '.repeat(12), ''])(
    'Given a blank or oversized password, when submitting directly on the registration URL, then explain the rule before sending credentials',
    async (value) => {
      openCatalogRoute('/register', undefined, null)
      fireEvent.change(await screen.findByLabelText('Password'), { target: { value } })
      fireEvent.submit(screen.getByRole('button', { name: 'Create account' }).closest('form')!)
      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Choose a nonblank password. Use at least 12 characters and at most 72 UTF-8 bytes.',
      )
      expect(registerAccount).not.toHaveBeenCalled()
    },
  )
  scenario.each([
    [new Error('This email is already registered.'), 'This email is already registered.'],
    ['offline', 'Could not create your account.'],
  ] as const)(
    'Given registration fails, when the visitor retries, then preserve the input and clear the previous error while retrying',
    async (failure, message) => {
      openCatalogRoute('/register', undefined, null)
      const email = await screen.findByLabelText('Email')
      const password = screen.getByLabelText('Password')
      fireEvent.change(email, { target: { value: 'driver@example.test' } })
      fireEvent.change(password, { target: { value: 'test-password-123' } })
      vi.mocked(registerAccount)
        .mockRejectedValueOnce(failure)
        .mockImplementationOnce(() => new Promise(() => {}))
      const button = screen.getByRole('button', { name: 'Create account' })
      fireEvent.submit(button.closest('form')!)
      expect(await screen.findByRole('alert')).toHaveTextContent(message)
      expect(email).toHaveValue('driver@example.test')
      expect(password).toHaveValue('test-password-123')
      fireEvent.submit(button.closest('form')!)
      await waitFor(() => expect(button).toBeDisabled())
      expect(screen.queryByRole('alert')).toBeNull()
    },
  )
})
