import { afterEach, describe, expect, test as scenario, vi } from 'vitest'
import { AccessError, protectedFetch, retryQuery } from './protected-fetch'
import { session } from './session'
import { adminSession } from '../../test/open-catalog-route'
describe('Protected API requests', () => {
  afterEach(() => vi.unstubAllGlobals())
  scenario(
    'Given an active admin, when sending a write, then its payload, headers and cancellation signal accompany the current bearer token',
    async () => {
      session.set(adminSession)
      const response = new Response(null, { status: 204 })
      const fetch = vi.fn().mockResolvedValue(response)
      vi.stubGlobal('fetch', fetch)
      const options = {
        method: 'PATCH',
        body: '{}',
        signal: new AbortController().signal,
        headers: { 'Content-Type': 'application/json' },
      }
      await expect(protectedFetch('/api/cars/id', options)).resolves.toBe(response)
      expect(fetch).toHaveBeenCalledExactlyOnceWith('/api/cars/id', {
        ...options,
        headers: { ...options.headers, Authorization: 'Bearer test-admin' },
      })
    },
  )
  scenario.each([
    [401, 'Your session expired. Sign in again.'],
    [403, 'Your account cannot perform this action.'],
  ] as const)(
    'Given a current token, when the server returns %i, then the client reports %s',
    async (status, message) => {
      session.set(adminSession)
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status })))
      await expect(protectedFetch('/api/categories', {})).rejects.toMatchObject({ status, message })
      expect(session.read()).toBe(status === 401 ? null : adminSession)
    },
  )
  scenario.each([200, 401])(
    'Given a request from an old account, when its response arrives with %i after another login, then it cannot publish data or expire the new session',
    async (status) => {
      session.set(adminSession)
      let resolve!: (r: Response) => void
      vi.stubGlobal(
        'fetch',
        vi.fn().mockImplementation(
          () =>
            new Promise((r) => {
              resolve = r
            }),
        ),
      )
      const result = protectedFetch('/api/categories', {})
      const next = { ...adminSession, accessToken: 'new' }
      session.set(next)
      resolve(new Response(null, { status }))
      await expect(result).rejects.toMatchObject({
        status,
        message: 'Your account changed. Try again.',
      })
      expect(session.read()).toBe(next)
    },
  )
  scenario(
    'Given query failures, when choosing a retry, then authorization errors stop immediately and transient errors allow three retries',
    () => {
      expect(retryQuery(0, new AccessError(401, 'expired'))).toBe(false)
      expect(retryQuery(0, new Error())).toBe(true)
      expect(retryQuery(2, new Error())).toBe(true)
      expect(retryQuery(3, new Error())).toBe(false)
    },
  )
})
