import { expect, test } from '@playwright/test'

const CAR_ID_PATTERN = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i

test('Given seeded cars, when a guest lists them, then public summaries are ordered', async ({
  request,
}) => {
  const response = await test.step('When a guest requests GET /cars', () => request.get('/cars'))

  const catalog = await test.step('Then the API returns a nonempty JSON catalog', async () => {
    expect(response.status()).toBe(200)
    expect(response.headers()['content-type']).toContain('application/json')
    const cars: unknown = await response.json()
    expect(Array.isArray(cars)).toBe(true)
    const result = cars as Array<Record<string, unknown>>
    expect(result.length).toBeGreaterThan(0)
    return result
  })

  await test.step('And every car has a public identifier, name, and availability', async () => {
    for (const car of catalog) {
      expect(car).toEqual(
        expect.objectContaining({
          id: expect.stringMatching(CAR_ID_PATTERN),
          name: expect.any(String),
          available: expect.any(Boolean),
        }),
      )
    }
  })

  await test.step('And cars are ordered by name', async () => {
    expect(catalog.map((car) => car.name)).toEqual(
      catalog
        .map((car) => car.name)
        .sort((left, right) => String(left).localeCompare(String(right))),
    )
  })
})
