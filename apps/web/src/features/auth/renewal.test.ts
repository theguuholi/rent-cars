import { afterEach, describe, expect, test as scenario, vi } from 'vitest'
import { deviceKey, restore, signOut, renew, onDeviceChange } from './renewal'
import { session } from './session'
import { protectedFetch } from './protected-fetch'
import { adminSession } from '../../test/open-catalog-route'

const csrf = () => new Response(null, { headers: { 'X-XSRF-TOKEN': 'proof' } })
const status = (code: number) => new Response(null, { status: code })
function device() {
  localStorage.setItem(deviceKey, 'device-1')
  session.set(adminSession)
}

describe('Device session renewal', () => {
  afterEach(() => vi.unstubAllGlobals())
  scenario(
    'Given two protected catalogs and an expired access token, when both requests return 401, then they rotate once and replay with the new bearer without invalidating the editor',
    async () => {
      device()
      const changed = vi.fn()
      const stop = session.subscribe(changed)
      const fetch = vi
        .fn()
        .mockResolvedValueOnce(status(401))
        .mockResolvedValueOnce(status(401))
        .mockResolvedValueOnce(csrf())
        .mockResolvedValueOnce(Response.json({ accessToken: 'fresh' }))
        .mockResolvedValueOnce(Response.json(adminSession.user))
        .mockResolvedValue(status(200))
      vi.stubGlobal('fetch', fetch)
      try {
        const results = await Promise.all(
          ['categories', 'specifications'].map((p) => protectedFetch('/api/' + p, {})),
        )
        expect(results.map((r) => r.status)).toEqual([200, 200])
        expect(fetch.mock.calls).toEqual([
          ...['categories', 'specifications'].map((p) => [
            '/api/' + p,
            { headers: { Authorization: 'Bearer test-admin' } },
          ]),
          ['/api/csrf'],
          ['/api/sessions/refresh', { method: 'POST', headers: { 'X-XSRF-TOKEN': 'proof' } }],
          ['/api/sessions/me', { headers: { Authorization: 'Bearer fresh' } }],
          ...['categories', 'specifications'].map((p) => [
            '/api/' + p,
            { headers: { Authorization: 'Bearer fresh' } },
          ]),
        ])
        expect(changed).not.toHaveBeenCalled()
      } finally {
        stop()
      }
    },
  )
  scenario.each([401, 500])(
    'Given renewal returns %i, when the session is restored, then only definitive rejection forgets the device',
    async (code) => {
      device()
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValueOnce(csrf()).mockResolvedValueOnce(status(code)),
      )
      if (code === 401) {
        await restore()
        expect(session.read()).toBeNull()
        expect(localStorage.getItem(deviceKey)).toBeNull()
      } else {
        await expect(restore()).rejects.toThrow('Could not renew your session. Try again.')
        expect(session.read()).toBe(adminSession)
        expect(localStorage.getItem(deviceKey)).toBe('device-1')
      }
    },
  )
  scenario(
    'Given a page reload with a device cookie, when restoration succeeds, then the current role is available before navigation',
    async () => {
      device()
      session.set(null)
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValueOnce(csrf())
          .mockResolvedValueOnce(Response.json({ accessToken: 'new' }))
          .mockResolvedValueOnce(Response.json(adminSession.user)),
      )
      await restore()
      expect(session.read()).toEqual({ ...adminSession, accessToken: 'new' })
    },
  )
  scenario.each([204, 500])(
    'Given a device session, when logout returns %i, then only confirmed revocation discards the identity',
    async (code) => {
      device()
      const fetch = vi.fn().mockResolvedValueOnce(csrf()).mockResolvedValueOnce(status(code))
      vi.stubGlobal('fetch', fetch)
      if (code === 204) {
        await signOut()
        expect(session.read()).toBeNull()
        expect(localStorage.getItem(deviceKey)).toBeNull()
      } else {
        await expect(signOut()).rejects.toThrow('Could not sign out. Try again.')
        expect(session.read()).toBe(adminSession)
      }
      expect(fetch).toHaveBeenLastCalledWith('/api/sessions/current', {
        method: 'DELETE',
        headers: { 'X-XSRF-TOKEN': 'proof' },
      })
    },
  )
  scenario(
    'Given a profile request in flight, when the session is cleared before the profile arrives, then renewal cannot restore the old account',
    async () => {
      device()
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValueOnce(csrf())
          .mockResolvedValueOnce(Response.json({ accessToken: 'late' }))
          .mockImplementationOnce(() => {
            session.set(null)
            return Response.json(adminSession.user)
          }),
      )
      await renew()
      expect(session.read()).toBeNull()
    },
  )
})

describe('Delayed session operations', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })
  scenario.each(['account', 'device', 'logout'] as const)(
    'Given a queued %s operation, when another login wins the cookie lock, then the stale operation never sends the cookie',
    async (change) => {
      device()
      const fetch = vi.fn()
      vi.stubGlobal('fetch', fetch)
      vi.spyOn(navigator.locks, 'request').mockImplementationOnce(async (_name, callback) => {
        if (change === 'account') session.set(adminSession)
        else localStorage.setItem(deviceKey, 'replacement')
        return (callback as () => Promise<void>)()
      })
      await (change === 'logout' ? signOut() : renew())
      expect(fetch).not.toHaveBeenCalled()
      expect(session.read()).toBe(adminSession)
    },
  )
  scenario(
    'Given an outstanding renewal, when a different account signs in before its rejection, then it keeps the replacement login',
    async () => {
      device()
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValueOnce(csrf())
          .mockImplementationOnce(() => {
            session.set({ ...adminSession, accessToken: 'replacement' })
            return status(401)
          }),
      )
      await renew()
      expect(session.read()?.accessToken).toBe('replacement')
      expect(localStorage.getItem(deviceKey)).toBe('device-1')
    },
  )
  scenario(
    'Given a previously rejected renewal, when the session retries later, then it performs a fresh exchange',
    async () => {
      device()
      const fetch = vi
        .fn()
        .mockRejectedValueOnce(new Error('offline'))
        .mockResolvedValueOnce(csrf())
        .mockResolvedValueOnce(status(401))
      vi.stubGlobal('fetch', fetch)
      await expect(renew()).rejects.toThrow('offline')
      await renew()
      expect(fetch).toHaveBeenCalledTimes(3)
      expect(session.read()).toBeNull()
    },
  )
  scenario.each(['id', 'role', 'email'] as const)(
    'Given a refreshed profile with a changed %s, when it arrives, then private data consumers observe the new identity',
    (key) => {
      device()
      const change = vi.fn()
      const stop = session.subscribe(change)
      try {
        session.renew({
          ...adminSession,
          user: { ...adminSession.user, [key]: key === 'role' ? 'USER' : 'different' },
        })
        expect(change).toHaveBeenCalledOnce()
      } finally {
        stop()
      }
    },
  )
  scenario(
    'Given an expired device session, when a protected request cannot renew, then it asks for sign-in without replay',
    async () => {
      device()
      const fetch = vi
        .fn()
        .mockResolvedValueOnce(status(401))
        .mockResolvedValueOnce(csrf())
        .mockResolvedValueOnce(status(401))
      vi.stubGlobal('fetch', fetch)
      await expect(protectedFetch('/api/categories', {})).rejects.toMatchObject({
        status: 401,
        message: 'Your session changed. Sign in again.',
      })
      expect(fetch).toHaveBeenCalledTimes(3)
    },
  )
  scenario(
    'Given a late 401 for an earlier bearer, when the same login already renewed, then replay once with its current token',
    async () => {
      device()
      const fetch = vi
        .fn()
        .mockImplementationOnce(() => {
          session.renew({ ...adminSession, accessToken: 'new' })
          return status(401)
        })
        .mockResolvedValueOnce(status(200))
      vi.stubGlobal('fetch', fetch)
      expect((await protectedFetch('/api/categories', {})).ok).toBe(true)
      expect(fetch.mock.calls).toEqual([
        ['/api/categories', { headers: { Authorization: 'Bearer test-admin' } }],
        ['/api/categories', { headers: { Authorization: 'Bearer new' } }],
      ])
    },
  )
  scenario(
    'Given a browser without an active account, when a protected endpoint returns 401, then no refresh is attempted with another device marker',
    async () => {
      device()
      session.set(null)
      const fetch = vi.fn().mockResolvedValue(status(401))
      vi.stubGlobal('fetch', fetch)
      await expect(protectedFetch('/api/categories', {})).rejects.toMatchObject({ status: 401 })
      expect(fetch).toHaveBeenCalledExactlyOnceWith('/api/categories', {})
    },
  )
  scenario(
    'Given storage changes from another tab, when its login identifier changes, then only that event reloads the router',
    () => {
      const reload = vi.fn()
      vi.stubGlobal('window', { location: { reload } })
      onDeviceChange(new StorageEvent('storage', { key: 'unrelated' }))
      expect(reload).not.toHaveBeenCalled()
      onDeviceChange(new StorageEvent('storage', { key: 'rent-cars.login' }))
      expect(reload).toHaveBeenCalledOnce()
    },
  )
})
