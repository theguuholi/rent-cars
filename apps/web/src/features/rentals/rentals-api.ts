import { protectedFetch } from '../auth/protected-fetch'

export interface Rental {
  id: string
  carId: string
  startDate: string
  expectedReturnDate: string
  endDate: string | null
  totalCents: number | null
}

async function request(path: string, options: RequestInit): Promise<Response> {
  const response = await protectedFetch(path, options)
  if (response.ok) return response
  let detail: string | undefined
  try {
    const body: { detail?: unknown } = await response.json()
    if (typeof body.detail === 'string') detail = body.detail
  } catch {
    // The status still gives a useful error when the response has no JSON body.
  }
  if (response.status === 409) {
    throw new Error(
      detail?.includes('Account already')
        ? 'You already have an open rental. Return it before renting another car.'
        : 'This car has already been rented. Refresh the car details.',
    )
  }
  throw new Error(detail || `Rental request failed (${response.status}). Try again.`)
}

export async function listRentals(signal?: AbortSignal): Promise<Rental[]> {
  return (await request('/api/rentals', { signal })).json()
}

export async function rentCar(carId: string, expectedReturnDate: string): Promise<Rental> {
  const response = await request('/api/rentals', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ carId, expectedReturnDate }),
  })
  return response.json()
}

export async function returnRental(id: string): Promise<Rental> {
  const response = await request(`/api/rentals/${encodeURIComponent(id)}/return`, {
    method: 'POST',
  })
  return response.json()
}
