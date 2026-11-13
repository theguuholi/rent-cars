import { expect, test } from '@playwright/test'

const webBaseUrl = process.env.RENT_CARS_WEB_URL ?? 'http://localhost:5173'

test('Given local tracing is enabled, when a visitor opens the catalog, then the API request carries trace context', async ({
  page,
}) => {
  const apiRequest = page.waitForRequest(
    (request) => request.url().includes('/api/cars') && request.method() === 'GET',
  )
  const exportResponse = page.waitForResponse((response) =>
    response.url().includes('/otel/v1/traces'),
  )

  await test.step('When the visitor opens the catalog', async () => {
    await page.goto(`${webBaseUrl}/cars`)
  })

  await test.step('Then the API request carries the browser trace ID', async () => {
    const request = await apiRequest
    expect(request.headers()['traceparent']).toMatch(/^00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]$/)
    await expect(page.getByRole('region', { name: 'Cars' })).toBeVisible()
    expect((await exportResponse).status()).toBe(200)
  })
})
