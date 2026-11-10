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
    [{ firstName: '  ' }, 'First name', 'Enter a first name of up to 80 characters.'],
    [{ firstName: 'a'.repeat(81) }, 'First name', 'Enter a first name of up to 80 characters.'],
    [{ lastName: '  ' }, 'Last name', 'Enter a last name of up to 80 characters.'],
    [{ lastName: 'a'.repeat(81) }, 'Last name', 'Enter a last name of up to 80 characters.'],
    [{ userName: 'ab' }, 'Username', 'Use 3–40 letters, numbers, dots, underscores or hyphens.'],
    [
      { userName: 'a'.repeat(41) },
      'Username',
      'Use 3–40 letters, numbers, dots, underscores or hyphens.',
    ],
    [{ userName: 'ab c' }, 'Username', 'Use 3–40 letters, numbers, dots, underscores or hyphens.'],
    [{ userName: '@abc' }, 'Username', 'Use 3–40 letters, numbers, dots, underscores or hyphens.'],
    [{ userName: 'abc@' }, 'Username', 'Use 3–40 letters, numbers, dots, underscores or hyphens.'],
    [{ driveLicense: '1234567890' }, 'CNH number', 'Enter the 11-digit CNH registration number.'],
    [{ driveLicense: '123456789012' }, 'CNH number', 'Enter the 11-digit CNH registration number.'],
    [{ driveLicense: 'a1234567890' }, 'CNH number', 'Enter the 11-digit CNH registration number.'],
    [{ driveLicense: '1234567890a' }, 'CNH number', 'Enter the 11-digit CNH registration number.'],
    [{ email: 'bad' }, 'Email', 'Enter a valid email address.'],
    [{ email: 'bad@example.test extra' }, 'Email', 'Enter a valid email address.'],
    [{ email: 'invalid foo@example.test' }, 'Email', 'Enter a valid email address.'],
    [{ email: '@example.test' }, 'Email', 'Enter a valid email address.'],
    [{ email: 'bad@.test' }, 'Email', 'Enter a valid email address.'],
    [{ email: 'bad@example.' }, 'Email', 'Enter a valid email address.'],
    [{ email: `${'a'.repeat(245)}@example.test` }, 'Email', 'Enter a valid email address.'],
    [
      { password: ' '.repeat(12), passwordConfirmation: ' '.repeat(12) },
      'Password',
      'Choose a nonblank password. Use at least 12 characters and at most 72 UTF-8 bytes.',
    ],
    [
      { password: 'a'.repeat(11), passwordConfirmation: 'a'.repeat(11) },
      'Password',
      'Choose a nonblank password. Use at least 12 characters and at most 72 UTF-8 bytes.',
    ],
    [
      { password: 'a'.repeat(73), passwordConfirmation: 'a'.repeat(73) },
      'Password',
      'Choose a nonblank password. Use at least 12 characters and at most 72 UTF-8 bytes.',
    ],
    [
      { password: 'é'.repeat(37), passwordConfirmation: 'é'.repeat(37) },
      'Password',
      'Choose a nonblank password. Use at least 12 characters and at most 72 UTF-8 bytes.',
    ],
    [
      { passwordConfirmation: 'different-password-123' },
      'Confirm password',
      'Passwords do not match.',
    ],
  ] as const)(
    'Given invalid registration data, when submitting, then explain %s before sending',
    async (input, field, message) => {
      openCatalogRoute('/register', undefined, null)
      await fill(input)
      submit()
      const control = screen.getByLabelText(field, { exact: true })
      await waitFor(() => expect(control).toHaveAttribute('aria-invalid', 'true'))
      expect(control).toHaveAccessibleDescription(
        field === 'Password'
          ? `Use at least 12 characters and at most 72 UTF-8 bytes. ${message}`
          : message,
      )
      expect(screen.getByRole('alert')).toHaveTextContent(message)
      expect(registerAccount).not.toHaveBeenCalled()
    },
  )

  scenario.each([
    { firstName: 'a'.repeat(80) },
    { lastName: 'a'.repeat(80) },
    { userName: 'a'.repeat(40) },
    { driveLicense: '1'.repeat(11) },
    { email: `${'a'.repeat(241)}@example.test` },
    { password: 'a'.repeat(12), passwordConfirmation: 'a'.repeat(12) },
    { password: 'a'.repeat(72), passwordConfirmation: 'a'.repeat(72) },
    { password: 'é'.repeat(36), passwordConfirmation: 'é'.repeat(36) },
    { firstName: ` ${'a'.repeat(80)} ` },
    { lastName: ` ${'a'.repeat(80)} ` },
    { userName: ' Test.Driver ' },
    { driveLicense: ' 12345678901 ' },
  ])('Given a valid boundary value, when registering, then submit %s', async (input) => {
    openCatalogRoute('/register', undefined, null)
    await fill(input)
    submit()
    await waitFor(() =>
      expect(registerAccount).toHaveBeenCalledExactlyOnceWith({ ...valid, ...input }),
    )
  })

  scenario(
    'Given a new form, when submitting empty fields, then show required errors without navigating',
    async () => {
      openCatalogRoute('/register', undefined, null)
      await screen.findByLabelText('First name')
      const fields = [
        'First name',
        'Last name',
        'Username',
        'CNH number',
        'Email',
        'Password',
        'Confirm password',
      ]
      for (const label of fields)
        expect(screen.getByLabelText(label, { exact: true })).toHaveValue('')
      const controls = new Map(
        fields.map((label) => [label, screen.getByLabelText(label, { exact: true })]),
      )
      const form = screen.getByRole('button', { name: 'Create account' }).closest('form')!
      const event = new Event('submit', { bubbles: true, cancelable: true })
      fireEvent(form, event)
      expect(event.defaultPrevented).toBe(true)
      await waitFor(() => expect(screen.getAllByRole('alert')).toHaveLength(fields.length))
      expect(controls.get('First name')).toHaveAccessibleDescription(
        'Enter a first name of up to 80 characters.',
      )
      expect(controls.get('Last name')).toHaveAccessibleDescription(
        'Enter a last name of up to 80 characters.',
      )
      expect(controls.get('Username')).toHaveAccessibleDescription(
        'Use 3–40 letters, numbers, dots, underscores or hyphens.',
      )
      expect(controls.get('CNH number')).toHaveAccessibleDescription(
        'Enter the 11-digit CNH registration number.',
      )
      expect(controls.get('Email')).toHaveAccessibleDescription('Enter a valid email address.')
      expect(controls.get('Confirm password')).toHaveAccessibleDescription(
        'Passwords do not match.',
      )
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
