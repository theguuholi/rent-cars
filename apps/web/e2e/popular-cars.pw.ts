import { expect, test } from '@playwright/test'

const webBaseUrl = process.env.RENT_CARS_WEB_URL ?? 'http://localhost:5173'

test('Given an unavailable ranking, when a visitor browses cars, then the catalog remains usable and recommendations can recover', async ({
  page,
}) => {
  await test.step('Given the ranking request fails', async () => {
    await page.route('**/api/cars/recommendations', (route) => route.fulfill({ status: 503 }))
  })
  await test.step('When the visitor opens the catalog, then filters remain usable', async () => {
    await page.goto(`${webBaseUrl}/cars`)
    await expect(
      page.getByRole('region', { name: 'Most rented cars' }).getByRole('alert'),
    ).toBeVisible()
    await expect(page.getByRole('textbox', { name: 'Name', exact: true })).toBeEnabled()
  })
  await test.step('When recommendations recover and the visitor retries, then the empty ranking is explained', async () => {
    await page.route('**/api/cars/recommendations', (route) => route.fulfill({ json: [] }))
    await page
      .getByRole('region', { name: 'Most rented cars' })
      .getByRole('button', { name: 'Try again' })
      .click()
    await expect(page.getByText('No recommendations yet. Browse the fleet below.')).toBeVisible()
  })
})

test('Given ranked available cars, when a visitor follows a recommendation on mobile, then the matching detail opens', async ({
  page,
  request,
}) => {
  let selected: { id: string; name: string }
  await test.step('Given two available cars in a controlled ranking response', async () => {
    const fleet = await request.get('/cars?size=2')
    expect(fleet.status()).toBe(200)
    const { content } = await fleet.json()
    expect(content.length).toBeGreaterThan(0)
    const ranked = [...content].reverse()
    selected = ranked[0]
    await page.route('**/api/cars/recommendations', (route) => route.fulfill({ json: ranked }))
    await page.setViewportSize({ width: 390, height: 844 })
  })
  await test.step('When the visitor opens the catalog, then ranking order and detail links are visible', async () => {
    await page.goto(`${webBaseUrl}/cars`)
    const ranking = page.getByRole('region', { name: 'Most rented cars' })
    await expect(ranking.getByRole('link').first()).toHaveText(selected.name)
    await ranking.getByRole('link').first().focus()
    await page.keyboard.press('Enter')
  })
  await test.step('Then the selected car detail opens', async () => {
    await expect(page).toHaveURL(`${webBaseUrl}/cars/${selected.id}`)
    await expect(page.getByRole('heading', { name: selected.name, exact: true })).toBeVisible()
  })
})
