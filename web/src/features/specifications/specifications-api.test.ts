import { afterEach, expect, describe, test as scenario, vi } from 'vitest'
import { deleteSpecification, listSpecifications, saveSpecification } from './specifications-api'

const input = { name: 'gps', description: 'Navigation' }
const item = { ...input, id: 'test-id', name: 'GPS' }
describe('Specifications API client', () => {
  afterEach(() => vi.unstubAllGlobals())

  scenario(
    'Given a specification catalog, when the client lists it, then it forwards the signal and returns the records',
    async () => {
      const fetch = vi.fn().mockResolvedValue(Response.json([item]))
      vi.stubGlobal('fetch', fetch)
      const signal = new AbortController().signal
      await expect(listSpecifications(signal)).resolves.toEqual([item])
      expect(fetch).toHaveBeenCalledWith('/api/specifications', { signal })
    },
  )

  scenario.each([
    [undefined, 'POST', '/api/specifications'],
    [item.id, 'PUT', '/api/specifications/test-id'],
  ] as const)(
    'Given specification ID %s, when the client saves, then it sends a flat JSON body using %s at %s',
    async (id, method, url) => {
      const fetch = vi.fn().mockResolvedValue(Response.json(item))
      vi.stubGlobal('fetch', fetch)
      await expect(saveSpecification(input, id)).resolves.toEqual(item)
      expect(fetch).toHaveBeenCalledWith(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(input),
      })
    },
  )

  scenario(
    'Given a specification UUID, when DELETE returns 204, then deletion completes without parsing a body',
    async () => {
      const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
      vi.stubGlobal('fetch', fetch)
      await expect(deleteSpecification(item.id)).resolves.toBeUndefined()
      expect(fetch).toHaveBeenCalledWith('/api/specifications/test-id', { method: 'DELETE' })
    },
  )

  scenario.each([
    [404, /no longer exists/],
    [409, /already exists/],
    [422, /Check the name/],
    [503, /503/],
  ] as const)(
    'Given HTTP %s, when saving fails, then the client reports an error matching %s',
    async (status, message) => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status })))
      await expect(saveSpecification(input)).rejects.toThrow(message)
    },
  )
})
