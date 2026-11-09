import { expect, test } from '@playwright/test'

test('public GET /cars lists seeded cars without authentication', async ({ request }) => {
  const response = await request.get('/cars')

  expect(response.status()).toBe(200)
  expect(response.headers()['content-type']).toContain('application/json')

  const cars: unknown = await response.json()
  expect(Array.isArray(cars)).toBe(true)
  const catalog = cars as Array<Record<string, unknown>>
  expect(catalog.length).toBeGreaterThan(0)

  for (const car of catalog) {
    expect(car).toEqual(
      expect.objectContaining({
        id: expect.stringMatching(/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i),
        name: expect.any(String),
        available: expect.any(Boolean),
      }),
    )
  }
  expect(catalog.map((car) => car.name)).toEqual(
    catalog.map((car) => car.name).sort((left, right) => String(left).localeCompare(String(right))),
  )
})
