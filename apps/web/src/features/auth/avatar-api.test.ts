import { afterEach, describe, expect, test as scenario, vi } from 'vitest'
import { readAvatar, saveAvatar } from './avatar-api'
import { session } from './session'
import { adminSession } from '../../test/open-catalog-route'
describe('Avatar API', () => {
  afterEach(() => vi.unstubAllGlobals())
  scenario.each([200, 404, 500])(
    'Given an avatar GET returns %i, when reading with cancellation, then return the blob, absence or an error',
    async (status) => {
      session.set(adminSession)
      const fetch = vi.fn().mockResolvedValue(new Response('png', { status }))
      vi.stubGlobal('fetch', fetch)
      const signal = new AbortController().signal
      if (status === 500)
        await expect(readAvatar(signal)).rejects.toThrow('Could not load your avatar.')
      else {
        const result = await readAvatar(signal)
        if (status === 404) expect(result).toBeNull()
        else expect(await result!.text()).toBe('png')
      }
      expect(fetch).toHaveBeenCalledExactlyOnceWith('/api/users/me/avatar', {
        signal,
        headers: { Authorization: 'Bearer test-admin' },
      })
    },
  )
  scenario(
    'Given a selected file, when saving, then send a replayable FormData image without overriding the browser boundary',
    async () => {
      session.set(adminSession)
      const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
      vi.stubGlobal('fetch', fetch)
      const file = new File(['png'], 'test.png', { type: 'image/png' })
      await saveAvatar(file)
      expect(fetch).toHaveBeenCalledTimes(1)
      const [path, request] = fetch.mock.calls[0]!
      expect(path).toBe('/api/users/me/avatar')
      expect(request.method).toBe('PUT')
      expect(request.headers).toEqual({ Authorization: 'Bearer test-admin' })
      expect([...request.body.entries()]).toEqual([['image', file]])
    },
  )
  scenario.each([
    [204, null],
    [413, 'Choose an image of at most 5 MiB.'],
    [422, 'Choose a valid JPEG or PNG of at most 4 megapixels.'],
    [500, 'Could not update your avatar. Try again.'],
  ] as const)(
    'Given avatar mutation returns %i, when deleting, then report its result',
    async (status, message) => {
      session.set(adminSession)
      const fetch = vi.fn().mockResolvedValue(new Response(null, { status }))
      vi.stubGlobal('fetch', fetch)
      if (message) await expect(saveAvatar(null)).rejects.toThrow(message)
      else await expect(saveAvatar(null)).resolves.toBeUndefined()
      expect(fetch).toHaveBeenCalledExactlyOnceWith('/api/users/me/avatar', {
        method: 'DELETE',
        headers: { Authorization: 'Bearer test-admin' },
      })
    },
  )
})
