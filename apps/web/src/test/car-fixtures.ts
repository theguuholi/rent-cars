export const categories = [{ id: 'cat', name: 'Sedan', description: null }]
export const specifications = [{ id: 'gps', name: 'GPS', description: 'Navigation' }]
import type { CarWithCatalog } from '../features/cars/cars-api'

export const car: CarWithCatalog = {
  category: categories[0],
  specifications,
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
