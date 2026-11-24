import { afterEach, describe, expect, test as scenario, vi } from 'vitest'
import { listRecommendedCars } from './recommendations-api'
import { car } from '../../test/car-fixtures'

describe('listRecommendedCars', () => {
  afterEach(() => vi.unstubAllGlobals())

  scenario(
    'Given ranked cars, when requesting recommendations, then the public endpoint preserves their order and receives cancellation',
    async () => {
      const cars = [
        { ...car, id: 'first' },
        { ...car, id: 'second' },
      ]
      const fetch = vi.fn().mockResolvedValue(Response.json(cars))
      vi.stubGlobal('fetch', fetch)
      const signal = new AbortController().signal
      await expect(listRecommendedCars(signal)).resolves.toEqual(cars)
      expect(fetch).toHaveBeenCalledExactlyOnceWith('/api/cars/recommendations', { signal })
    },
  )

  scenario(
    'Given no ranked cars, when requesting recommendations, then an empty list is preserved',
    async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json([])))
      await expect(listRecommendedCars(new AbortController().signal)).resolves.toEqual([])
    },
  )

  scenario(
    'Given an HTTP failure, when requesting recommendations, then the caller receives an actionable error',
    async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 503 })))
      await expect(listRecommendedCars(new AbortController().signal)).rejects.toThrow(
        'Could not load recommendations.',
      )
    },
  )

  scenario(
    'Given a cancelled request, when fetch rejects, then the cancellation reaches the query',
    async () => {
      const error = new DOMException('Aborted', 'AbortError')
      vi.stubGlobal('fetch', vi.fn().mockRejectedValue(error))
      await expect(listRecommendedCars(new AbortController().signal)).rejects.toBe(error)
    },
  )
})
