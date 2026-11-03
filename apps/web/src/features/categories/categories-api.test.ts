import { afterEach, expect, describe, test as scenario, vi } from 'vitest'
import { listCategories } from './categories-api'

describe('Categories API client', () => {
  afterEach(() => vi.unstubAllGlobals())

  scenario(
    'Given a category response, when listCategories runs, then it forwards the signal and returns the decoded catalog',
    async () => {
      const signal = new AbortController().signal
      const catalog = [{ id: 'suv', name: 'SUV', description: null }]
      const fetchMock = vi.fn().mockResolvedValue(Response.json(catalog))
      vi.stubGlobal('fetch', fetchMock)

      await expect(listCategories(signal)).resolves.toEqual(catalog)
      expect(fetchMock).toHaveBeenCalledWith('/api/categories', { signal })
    },
  )

  scenario(
    'Given a failed HTTP response, when listCategories reads its status, then the error includes that status',
    async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 503 })))

      await expect(listCategories(new AbortController().signal)).rejects.toThrow(
        'Failed to fetch categories (503)',
      )
    },
  )

  scenario(
    'Given an offline backend, when fetch rejects, then listCategories propagates the network error',
    async () => {
      const error = new TypeError('Offline')
      vi.stubGlobal('fetch', vi.fn().mockRejectedValue(error))

      await expect(listCategories(new AbortController().signal)).rejects.toBe(error)
    },
  )

  scenario(
    'Given an aborted request, when fetch rejects, then listCategories preserves the cancellation reason',
    async () => {
      const controller = new AbortController()
      controller.abort()
      vi.stubGlobal('fetch', vi.fn().mockRejectedValue(controller.signal.reason))

      await expect(listCategories(controller.signal)).rejects.toBe(controller.signal.reason)
    },
  )

  scenario(
    'Given HTTP 200 with malformed JSON, when listCategories decodes it, then the promise rejects with a syntax error',
    async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('invalid JSON')))

      await expect(listCategories(new AbortController().signal)).rejects.toThrow(SyntaxError)
    },
  )
})
