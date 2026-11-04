import { afterEach, describe, expect, test as scenario, vi } from 'vitest'
import { signIn } from './auth-api'
const email = 'driver@example.test',
  password = 'local-dev-123',
  csrf = 'csrf-token'
const user = { id: 'driver', email, role: 'USER' }
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
      await expect(signIn(email, password)).rejects.toThrow('Could not start sign-in. Try again.')
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
