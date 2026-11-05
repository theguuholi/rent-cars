import { afterEach, describe, expect, test as scenario, vi } from 'vitest'
import { protectedFetch } from '../auth/protected-fetch'
import { carImageUrl, deleteCarImage, listCarImages, uploadCarImage } from './car-images-api'
vi.mock('../auth/protected-fetch')
afterEach(() => {
  vi.resetAllMocks()
  vi.unstubAllGlobals()
})
describe('carImageUrl', () => {
  scenario(
    'Given IDs containing URL delimiters, when building a photo URL, then encode both path segments',
    () => {
      expect(carImageUrl('car/a', 'image?b')).toBe('/api/cars/car%2Fa/images/image%3Fb')
    },
  )
})
describe('listCarImages', () => {
  scenario(
    'Given public photos, when listing, then forward cancellation without requiring authentication',
    async () => {
      const fetcher = vi.fn().mockResolvedValue(Response.json([{ id: 'one' }]))
      vi.stubGlobal('fetch', fetcher)
      const signal = new AbortController().signal
      expect(await listCarImages('car/a', signal)).toEqual([{ id: 'one' }])
      expect(fetcher).toHaveBeenCalledExactlyOnceWith('/api/cars/car%2Fa/images', { signal })
      expect(protectedFetch).not.toHaveBeenCalled()
    },
  )
  scenario(
    'Given a failed gallery request, when listing, then provide a readable retry message',
    async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 500 })))
      await expect(listCarImages('car', new AbortController().signal)).rejects.toThrow(
        'Could not load car photos.',
      )
    },
  )
})
describe('uploadCarImage', () => {
  scenario(
    'Given a selected photo, when uploading, then send an authenticated multipart POST with the browser boundary',
    async () => {
      vi.mocked(protectedFetch).mockResolvedValue(new Response(null, { status: 201 }))
      const file = new File(['image'], 'car.png', { type: 'image/png' })
      await uploadCarImage('car/a', file)
      expect(protectedFetch).toHaveBeenCalledExactlyOnceWith('/api/cars/car%2Fa/images', {
        method: 'POST',
        body: expect.any(FormData),
      })
      const body = vi.mocked(protectedFetch).mock.calls[0][1]?.body as FormData
      expect(body.get('image')).toBe(file)
      expect([...body.keys()]).toEqual(['image'])
    },
  )
  scenario.each([
    [403, 'Only administrators can change car photos.'],
    [404, 'This car or photo no longer exists.'],
    [413, 'Choose an image of at most 5 MiB.'],
    [422, 'Choose a valid JPEG or PNG of at most 4 megapixels.'],
    [500, 'Could not update car photos. Try again.'],
  ])('Given HTTP %s, when uploading, then explain %s', async (status, message) => {
    vi.mocked(protectedFetch).mockResolvedValue(new Response(null, { status: Number(status) }))
    await expect(uploadCarImage('car', new File(['image'], 'car.png'))).rejects.toThrow(message)
  })
})
describe('deleteCarImage', () => {
  scenario(
    'Given a saved photo, when deleting, then use its car and image IDs and accept an empty 204',
    async () => {
      vi.mocked(protectedFetch).mockResolvedValue(new Response(null, { status: 204 }))
      await deleteCarImage('car/a', 'image/b')
      expect(protectedFetch).toHaveBeenCalledExactlyOnceWith('/api/cars/car%2Fa/images/image%2Fb', {
        method: 'DELETE',
      })
    },
  )
})
