import { randomUUID } from 'node:crypto'
import { expect, type APIRequestContext } from '@playwright/test'

// Each rental journey owns its car so parallel tests cannot rent the same seed.
export async function createRentalCar(request: APIRequestContext): Promise<string> {
  const csrf = await request.get('/csrf')
  expect(csrf.status()).toBe(204)
  const login = await request.post('/sessions', {
    headers: { 'X-XSRF-TOKEN': csrf.headers()['x-xsrf-token'] },
    data: {
      email: process.env.RENT_CARS_ADMIN_EMAIL ?? 'test@test',
      password: process.env.RENT_CARS_ADMIN_PASSWORD ?? 'test@tesT@dev',
    },
  })
  expect(login.status()).toBe(200)
  const { accessToken } = await login.json()
  const headers = { Authorization: `Bearer ${accessToken}` }
  const categories = await request.get('/categories', { headers })
  expect(categories.status()).toBe(200)
  const [category] = await categories.json()
  expect(category).toBeDefined()

  const name = `QA rental ${randomUUID()}`
  const car = await request.post('/cars', {
    headers,
    data: {
      name,
      description: 'A dedicated car for one rental journey',
      brand: 'QA',
      licensePlate: randomUUID().replaceAll('-', '').slice(0, 20).toUpperCase(),
      available: true,
      dailyRateCents: 15000,
      fineAmountCents: 500,
      categoryId: category.id,
      specificationIds: [],
    },
  })
  expect(car.status()).toBe(201)
  return name
}
