import { afterEach, describe, expect, test as scenario, vi } from 'vitest'
import { registerAccount, requestPasswordReset, resetPassword, signIn } from './auth-api'
const email = 'driver@example.test',
  password = 'local-dev-123',
  csrf = 'csrf-token'
const user = { id: 'driver', email, role: 'USER' }
const registration = {
  firstName: 'Test',
  lastName: 'Driver',
  userName: 'test.driver',
  driveLicense: '01234567890',
  email,
  password,
  passwordConfirmation: password,
}
const bootstrap = () => new Response(null, { status: 204, headers: { 'X-XSRF-TOKEN': csrf } })
describe('signIn', () => {
  afterEach(() => vi.unstubAllGlobals())
  scenario(
    'Given valid credentials, when signing in, then CSRF, password verification and profile lookup run in order',
    async () => {
      const fetch = vi
        .fn()
        .mockResolvedValueOnce(bootstrap())
        .mockResolvedValueOnce(Response.json({ accessToken: 'signed' }))
        .mockResolvedValueOnce(Response.json(user))
      vi.stubGlobal('fetch', fetch)
      await expect(signIn(email, password)).resolves.toEqual({ accessToken: 'signed', user })
      expect(localStorage.getItem('rent-cars.login')).toMatch(/^[a-f0-9-]{36}$/)
      expect(fetch.mock.calls).toEqual([
        ['/api/csrf'],
        [
          '/api/sessions',
          {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf },
            body: JSON.stringify({ email, password }),
          },
        ],
        ['/api/sessions/me', { headers: { Authorization: 'Bearer signed' } }],
      ])
    },
  )
  scenario.each([
    new Response(null, { status: 500, headers: { 'X-XSRF-TOKEN': csrf } }),
    new Response(),
  ])(
    'Given an invalid bootstrap, when signing in, then credentials are withheld',
    async (response) => {
      const fetch = vi.fn().mockResolvedValue(response)
      vi.stubGlobal('fetch', fetch)
      await expect(signIn(email, password)).rejects.toThrow(
        'Could not get a security token. Try again.',
      )
      expect(fetch).toHaveBeenCalledTimes(1)
    },
  )
  scenario.each([
    [401, 'Invalid email or password.'],
    [403, 'Security token expired. Try signing in again.'],
    [422, 'Could not sign in. Check your details and try again.'],
  ] as const)(
    'Given login status %i, when authenticating, then the user sees %s',
    async (status, message) => {
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValueOnce(bootstrap())
          .mockResolvedValueOnce(new Response(null, { status })),
      )
      await expect(signIn(email, password)).rejects.toThrow(message)
    },
  )
  scenario(
    'Given a token but an unavailable profile, when finishing login, then no identity is returned',
    async () => {
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValueOnce(bootstrap())
          .mockResolvedValueOnce(Response.json({ accessToken: 'signed' }))
          .mockResolvedValueOnce(new Response(null, { status: 401 })),
      )
      await expect(signIn(email, password)).rejects.toThrow(
        'Could not verify your profile. Try signing in again.',
      )
    },
  )
})

describe('Driver registration', () => {
  afterEach(() => vi.unstubAllGlobals())
  scenario(
    'Given a CSRF token, when registering, then POST the complete driver details with the matching header',
    async () => {
      const fetch = vi
        .fn()
        .mockResolvedValueOnce(bootstrap())
        .mockResolvedValueOnce(new Response(null, { status: 201 }))
      vi.stubGlobal('fetch', fetch)
      await expect(registerAccount(registration)).resolves.toBeUndefined()
      expect(fetch.mock.calls).toEqual([
        ['/api/csrf'],
        [
          '/api/users',
          {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf },
            body: JSON.stringify(registration),
          },
        ],
      ])
    },
  )
  scenario.each([
    [409, 'An account with these details already exists.'],
    [422, 'Check the registration fields and try again.'],
    [403, 'Could not create your account. Try again.'],
    [500, 'Could not create your account. Try again.'],
  ] as const)(
    'Given registration returns %i, when the driver submits, then report %s',
    async (status, message) => {
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValueOnce(bootstrap())
          .mockResolvedValueOnce(new Response(null, { status })),
      )
      await expect(registerAccount(registration)).rejects.toThrow(message)
    },
  )
})

describe('Password recovery', () => {
  afterEach(() => vi.unstubAllGlobals())

  scenario(
    'Given an email, when recovery is requested, then send JSON with CSRF and accept an empty response',
    async () => {
      const fetch = vi
        .fn()
        .mockResolvedValueOnce(bootstrap())
        .mockResolvedValueOnce(new Response(null, { status: 202 }))
      vi.stubGlobal('fetch', fetch)
      await expect(requestPasswordReset(email)).resolves.toBeUndefined()
      expect(fetch.mock.calls).toEqual([
        ['/api/csrf'],
        [
          '/api/password-recovery',
          {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf },
            body: JSON.stringify({ email }),
          },
        ],
      ])
    },
  )

  scenario(
    'Given a failed request, when asking for recovery, then show a retry error',
    async () => {
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValueOnce(bootstrap())
          .mockResolvedValueOnce(new Response(null, { status: 500 })),
      )
      await expect(requestPasswordReset(email)).rejects.toThrow(
        'Could not request a reset link. Try again.',
      )
    },
  )

  const reset = {
    token: 'one-time-token',
    password: 'replacement-password-123',
    passwordConfirmation: 'replacement-password-123',
  }
  scenario(
    'Given a token and new password, when resetting, then send JSON with CSRF and accept 204',
    async () => {
      const fetch = vi
        .fn()
        .mockResolvedValueOnce(bootstrap())
        .mockResolvedValueOnce(new Response(null, { status: 204 }))
      vi.stubGlobal('fetch', fetch)
      await expect(resetPassword(reset)).resolves.toBeUndefined()
      expect(fetch.mock.calls).toEqual([
        ['/api/csrf'],
        [
          '/api/password-recovery/reset',
          {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf },
            body: JSON.stringify(reset),
          },
        ],
      ])
    },
  )

  scenario.each([
    [400, 'Reset link is invalid or expired. Request a new one.'],
    [422, 'Check the new password and try again.'],
    [500, 'Could not reset your password. Try again.'],
  ] as const)('Given reset status %i, when submitting, then show %s', async (status, message) => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(bootstrap())
        .mockResolvedValueOnce(new Response(null, { status })),
    )
    await expect(resetPassword(reset)).rejects.toThrow(message)
  })
})
