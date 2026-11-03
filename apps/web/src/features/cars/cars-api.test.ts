import { afterEach, describe, expect, test as scenario, vi } from 'vitest'
import { createCar, deleteCar, getCar, listCars, updateCar, type CarInput } from './cars-api'

const input: CarInput = {
  name: 'Argo',
  brand: 'Fiat',
  description: 'Compact',
  licensePlate: 'ABC1D23',
  dailyRateCents: 15050,
  fineAmountCents: 0,
  available: true,
  categoryId: 'hatch',
  specificationIds: ['gps'],
}
const car = { ...input, id: 'a/b' }
function mockResponse(response: Response) {
  const fetch = vi.fn().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetch)
  return fetch
}

describe('Cars HTTP client', () => {
  afterEach(() => vi.unstubAllGlobals())

  scenario(
    'Given registered cars, when listing, then the client forwards cancellation and decodes the catalog',
    async () => {
      const fetch = mockResponse(Response.json([car]))
      const signal = new AbortController().signal
      await expect(listCars(signal)).resolves.toEqual([car])
      expect(fetch).toHaveBeenCalledWith('/api/cars', { signal })
    },
  )

  scenario(
    'Given a record ID, when reading a car, then the URL is encoded and the signal forwarded',
    async () => {
      const fetch = mockResponse(Response.json(car))
      const signal = new AbortController().signal
      await expect(getCar(car.id, signal)).resolves.toEqual(car)
      expect(fetch).toHaveBeenCalledWith('/api/cars/a%2Fb', { signal })
    },
  )

  scenario(
    'Given car details, when registering, then cents and catalog IDs are sent unchanged as JSON',
    async () => {
      const fetch = mockResponse(Response.json(car, { status: 201 }))
      await expect(createCar(input)).resolves.toEqual(car)
      expect(fetch).toHaveBeenCalledWith('/api/cars', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(input),
      })
    },
  )

  scenario(
    'Given a cleared selection, when patching, then only the provided fields are sent',
    async () => {
      const patch = { specificationIds: [] }
      const updated = { ...car, ...patch }
      const fetch = mockResponse(Response.json(updated))
      await expect(updateCar(car.id, patch)).resolves.toEqual(updated)
      expect(fetch).toHaveBeenCalledWith('/api/cars/a%2Fb', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(patch),
      })
    },
  )

  scenario(
    'Given an existing car, when deletion returns no content, then the client completes without decoding JSON',
    async () => {
      const fetch = mockResponse(new Response(null, { status: 204 }))
      await expect(deleteCar(car.id)).resolves.toBeUndefined()
      expect(fetch).toHaveBeenCalledWith('/api/cars/a%2Fb', { method: 'DELETE' })
    },
  )

  scenario.each([
    [400, 'selected catalog'],
    [404, 'no longer exists'],
    [409, 'already exists'],
    [422, 'cannot change'],
    [500, '500'],
  ] as const)(
    'Given HTTP %s, when the API rejects a write, then the message includes "%s"',
    async (status, message) => {
      mockResponse(new Response(null, { status }))
      await expect(createCar(input)).rejects.toThrow(message)
    },
  )
})
