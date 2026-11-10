import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, test as scenario, vi } from 'vitest'
import { openCatalogRoute } from '../../../../test/open-catalog-route'
import { registerAccount } from '../../auth-api'
import { session } from '../../session'

vi.mock('../../auth-api')

const valid = {
  firstName: 'Test',
  lastName: 'Driver',
  userName: 'test.driver',
  driveLicense: '01234567890',
  email: 'driver@example.test',
  password: 'test-password-123',
  passwordConfirmation: 'test-password-123',
}

async function fill(overrides: Partial<typeof valid> = {}) {
  await screen.findByLabelText('First name')
  for (const [label, value] of Object.entries({
    'First name': valid.firstName,
    'Last name': valid.lastName,
    Username: valid.userName,
    'CNH number': valid.driveLicense,
    Email: valid.email,
    Password: valid.password,
    'Confirm password': valid.passwordConfirmation,
  })) {
    const name = (
      {
        'First name': 'firstName',
        'Last name': 'lastName',
        Username: 'userName',
        'CNH number': 'driveLicense',
        Email: 'email',
        Password: 'password',
        'Confirm password': 'passwordConfirmation',
      } as Record<string, keyof typeof valid>
    )[label]
    fireEvent.change(screen.getByLabelText(label, { exact: true }), {
      target: { value: overrides[name] ?? value },
    })
  }
}

function submit() {
  fireEvent.submit(screen.getByRole('button', { name: 'Create account' }).closest('form')!)
}

describe('RegisterPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
    vi.unstubAllGlobals()
  })

  scenario(
    'Given valid driver details, when registering, then submit once and offer sign-in',
    async () => {
      const history = openCatalogRoute('/login', undefined, null)
      fireEvent.click(await screen.findByRole('link', { name: 'Create an account' }))
      await fill()
      expect(history.location.pathname).toBe('/register')
      expect(screen.getByLabelText('First name')).toHaveAttribute('autocomplete', 'given-name')
      expect(screen.getByLabelText('Last name')).toHaveAttribute('autocomplete', 'family-name')
      expect(screen.getByLabelText('CNH number')).toHaveAttribute('maxlength', '11')
      expect(screen.getByLabelText('Confirm password')).toHaveAttribute('maxlength', '72')
      expect(screen.getByLabelText('Password', { exact: true })).toHaveAccessibleDescription(
        'Use at least 12 characters and at most 72 UTF-8 bytes.',
      )
      let finish!: () => void
      vi.mocked(registerAccount).mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finish = resolve
          }),
      )
      submit()
      await waitFor(() =>
        expect(screen.getByRole('button', { name: 'Create account' })).toBeDisabled(),
      )
      expect(registerAccount).toHaveBeenCalledExactlyOnceWith(valid)
      await act(async () => finish())
      expect(await screen.findByRole('status')).toHaveTextContent(
        'Your account is ready. Sign in to continue.',
      )
      expect(screen.queryByLabelText('Password', { exact: true })).toBeNull()
      expect(session.read()).toBeNull()
      fireEvent.click(screen.getByRole('link', { name: 'Back to sign in' }))
      expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeVisible()
    },
  )

  scenario.each([
    [{ firstName: '  ' }, 'Enter a first name'],
    [{ lastName: '  ' }, 'Enter a last name'],
    [{ userName: 'x' }, 'Use 3–40'],
    [{ driveLicense: '123' }, '11-digit CNH'],
    [{ email: 'bad' }, 'valid email'],
    [{ password: 'short', passwordConfirmation: 'short' }, 'Choose a nonblank password'],
    [
      { password: 'é'.repeat(37), passwordConfirmation: 'é'.repeat(37) },
      'Choose a nonblank password',
    ],
    [{ passwordConfirmation: 'different-password-123' }, 'Passwords do not match.'],
  ] as const)(
    'Given invalid registration data, when submitting, then explain %s before sending',
    async (input, message) => {
      openCatalogRoute('/register', undefined, null)
      await fill(input)
      submit()
      expect(await screen.findByRole('alert', { name: '' })).toHaveTextContent(message)
      expect(registerAccount).not.toHaveBeenCalled()
    },
  )

  scenario(
    'Given registration fails, when retrying, then preserve the form and clear the previous error',
    async () => {
      openCatalogRoute('/register', undefined, null)
      await fill()
      vi.mocked(registerAccount)
        .mockRejectedValueOnce(new Error('An account with these details already exists.'))
        .mockImplementationOnce(() => new Promise(() => {}))
      submit()
      expect(await screen.findByRole('alert')).toHaveTextContent(
        'An account with these details already exists.',
      )
      expect(screen.getByLabelText('CNH number')).toHaveValue(valid.driveLicense)
      submit()
      await waitFor(() =>
        expect(screen.getByRole('button', { name: 'Create account' })).toBeDisabled(),
      )
      expect(screen.queryByRole('alert')).toBeNull()
    },
  )
  scenario(
    'Given an unexpected API failure, when registering, then show a safe generic error',
    async () => {
      openCatalogRoute('/register', undefined, null)
      await fill()
      vi.mocked(registerAccount).mockRejectedValueOnce('offline')
      submit()
      expect(await screen.findByRole('alert')).toHaveTextContent('Could not create your account.')
      expect(screen.getByLabelText('Email')).toHaveValue(valid.email)
    },
  )
})
