import { afterEach, expect, it, vi } from 'vitest'
import { deleteSpecification, listSpecifications, saveSpecification } from './specifications-api'

const input = { name: 'gps', description: 'Navigation' }
const item = { ...input, id: 'test-id', name: 'GPS' }
afterEach(() => vi.unstubAllGlobals())

it('reads the catalog using the caller’s cancellation signal', async () => {
  const fetch = vi.fn().mockResolvedValue(Response.json([item]))
  vi.stubGlobal('fetch', fetch)
  const signal = new AbortController().signal
  await expect(listSpecifications(signal)).resolves.toEqual([item])
  expect(fetch).toHaveBeenCalledWith('/api/specifications', { signal })
})

it.each([
  [undefined, 'POST', '/api/specifications'],
  [item.id, 'PUT', '/api/specifications/test-id'],
] as const)('saves the flat request body for id %s', async (id, method, url) => {
  const fetch = vi.fn().mockResolvedValue(Response.json(item))
  vi.stubGlobal('fetch', fetch)
  await expect(saveSpecification(input, id)).resolves.toEqual(item)
  expect(fetch).toHaveBeenCalledWith(url, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
})

it('deletes by UUID and accepts an empty 204 response', async () => {
  const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
  vi.stubGlobal('fetch', fetch)
  await expect(deleteSpecification(item.id)).resolves.toBeUndefined()
  expect(fetch).toHaveBeenCalledWith('/api/specifications/test-id', { method: 'DELETE' })
})

it.each([
  [404, /no longer exists/],
  [409, /already exists/],
  [422, /Check the name/],
  [503, /503/],
] as const)('reports HTTP %s to the caller', async (status, message) => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status })))
  await expect(saveSpecification(input)).rejects.toThrow(message)
})
