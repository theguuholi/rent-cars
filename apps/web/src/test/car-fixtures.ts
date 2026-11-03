import type { Car } from '../features/cars/cars-api'

export const car: Car = {
  id: 'second',
  name: 'Corolla',
  description: 'Family sedan',
  brand: 'Toyota',
  licensePlate: 'ABC1234',
  available: false,
  categoryId: 'cat',
  specificationIds: ['gps'],
  dailyRateCents: 15050,
  fineAmountCents: 500,
}
export const categories = [{ id: 'cat', name: 'Sedan', description: null }]
export const specifications = [{ id: 'gps', name: 'GPS', description: 'Navigation' }]
