import type { Car } from './cars-api'

export async function listRecommendedCars(signal: AbortSignal): Promise<Car[]> {
  const response = await fetch('/api/cars/recommendations', { signal })
  if (!response.ok) throw new Error('Could not load recommendations.')
  return response.json()
}
