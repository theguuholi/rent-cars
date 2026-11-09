import { afterEach, describe, expect, test as scenario, vi } from 'vitest'
import { protectedFetch } from '../auth/protected-fetch'
import { listRentals, rentCar, returnRental } from './rentals-api'

vi.mock('../auth/protected-fetch')
afterEach(() => vi.resetAllMocks())

describe('rentals API', () => {
  scenario(
    'Given a signed-in driver, when listing rentals, then the query forwards its signal',
    async () => {
      const signal = new AbortController().signal
      vi.mocked(protectedFetch).mockResolvedValue(Response.json([{ id: 'rental' }]))
      expect(await listRentals(signal)).toEqual([{ id: 'rental' }])
      expect(protectedFetch).toHaveBeenCalledExactlyOnceWith('/api/rentals', { signal })
    },
  )

  scenario(
    'Given an available car, when renting, then the expected instant is sent as JSON',
    async () => {
      vi.mocked(protectedFetch).mockResolvedValue(Response.json({ id: 'rental' }, { status: 201 }))
      expect(await rentCar('car', '2026-11-12T15:00:00.000Z')).toEqual({ id: 'rental' })
      expect(protectedFetch).toHaveBeenCalledExactlyOnceWith('/api/rentals', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ carId: 'car', expectedReturnDate: '2026-11-12T15:00:00.000Z' }),
      })
    },
  )

  scenario('Given an open rental, when returning, then the request has no body', async () => {
    vi.mocked(protectedFetch).mockResolvedValue(Response.json({ id: 'rental' }))
    expect(await returnRental('rent/al')).toEqual({ id: 'rental' })
    expect(protectedFetch).toHaveBeenCalledExactlyOnceWith('/api/rentals/rent%2Fal/return', {
      method: 'POST',
    })
  })

  scenario.each([
    [409, { detail: 'Car is unavailable' }, 'This car has already been rented.'],
    [409, { detail: 'Account already has an open rental' }, 'You already have an open rental.'],
    [409, {}, 'This car has already been rented.'],
    [
      422,
      { detail: 'Expected return must be at least 24 hours after the start' },
      'Expected return must',
    ],
    [500, {}, 'Rental request failed (500).'],
    [422, { detail: 42 }, 'Rental request failed (422).'],
  ])(
    'Given HTTP %i, when renting, then the API explains the failure',
    async (status, body, message) => {
      vi.mocked(protectedFetch).mockResolvedValue(Response.json(body, { status }))
      await expect(rentCar('car', '2026-11-12T15:00:00Z')).rejects.toThrow(message)
    },
  )

  scenario(
    'Given a non-JSON server failure, when listing, then the status remains visible',
    async () => {
      vi.mocked(protectedFetch).mockResolvedValue(new Response(null, { status: 503 }))
      await expect(listRentals()).rejects.toThrow('Rental request failed (503).')
    },
  )
})
