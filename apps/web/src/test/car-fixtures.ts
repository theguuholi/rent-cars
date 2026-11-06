export const categories = [{ id: 'cat', name: 'Sedan', description: null }]
export const specifications = [{ id: 'gps', version: 0, name: 'GPS', description: 'Navigation' }]
import type { CarWithCatalog } from '../features/cars/cars-api'

export const car: CarWithCatalog = {
  images: [],
  category: categories[0],
  specifications,
  version: 0,
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
