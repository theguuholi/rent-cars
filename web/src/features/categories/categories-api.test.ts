import { afterEach, expect, it, vi } from 'vitest'
import { listCategories } from './categories-api'

afterEach(() => vi.unstubAllGlobals())

it('requests the catalog with the caller’s signal and reads its JSON', async () => {
  const signal = new AbortController().signal
  const catalog = [{ name: 'SUV', description: null }]
  const fetchMock = vi.fn().mockResolvedValue(Response.json(catalog))
  vi.stubGlobal('fetch', fetchMock)

  await expect(listCategories(signal)).resolves.toEqual(catalog)
  expect(fetchMock).toHaveBeenCalledWith('/api/categories', { signal })
})

it('reports the HTTP status when the backend responds with an error', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 503 })))

  await expect(listCategories(new AbortController().signal)).rejects.toThrow(
    'Failed to fetch categories (503)',
  )
})

it('propagates a network failure', async () => {
  const error = new TypeError('Offline')
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(error))

  await expect(listCategories(new AbortController().signal)).rejects.toBe(error)
})

it('preserves the cancellation reason from fetch', async () => {
  const controller = new AbortController()
  controller.abort()
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(controller.signal.reason))

  await expect(listCategories(controller.signal)).rejects.toBe(controller.signal.reason)
})

it('rejects a successful response containing malformed JSON', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('invalid JSON')))

  await expect(listCategories(new AbortController().signal)).rejects.toThrow(SyntaxError)
})
