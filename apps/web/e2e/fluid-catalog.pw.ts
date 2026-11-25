import { expect, test } from '@playwright/test'
const webBaseUrl = process.env.RENT_CARS_WEB_URL ?? 'http://localhost:5173'
for (const width of [390, 1440]) {
  test(`Given a ${width}px catalog, when opening a card image or using Enter, then the same car detail opens`, async ({
    page,
    request,
  }) => {
    const response = await request.get('/cars?size=1')
    expect(response.ok()).toBe(true)
    const {
      content: [car],
    } = await response.json()
    await test.step('Given the fleet is visible', async () => {
      await page.setViewportSize({ width, height: 900 })
      await page.goto(`${webBaseUrl}/cars`)
    })
    const catalog = page.getByRole('region', { name: 'Cars', exact: true })
    await test.step('When clicking the image away from the name, then the detail opens', async () => {
      await catalog.getByRole('link', { name: car.name, exact: true }).locator('img').click()
      await expect(page).toHaveURL(new RegExp(`/cars/${car.id}$`))
      await expect(page.getByRole('heading', { name: car.name, exact: true })).toBeVisible()
    })
    await test.step('When returning and pressing Enter on the card, then keyboard navigation works too', async () => {
      await page.goBack()
      const link = catalog.getByRole('link', { name: car.name, exact: true })
      await link.focus()
      await expect(link).toBeFocused()
      await page.keyboard.press('Enter')
      await expect(page).toHaveURL(new RegExp(`/cars/${car.id}$`))
    })
  })
}
test('Given one search field, when searching by brand and category, then server results and refresh preserve the query', async ({
  page,
}) => {
  await test.step('Given the public fleet', async () => {
    await page.goto(`${webBaseUrl}/cars`)
  })
  const catalog = page.getByRole('region', { name: 'Cars', exact: true })
  await test.step('When searching by brand, then matching cars appear', async () => {
    await page.getByRole('textbox', { name: 'Search cars' }).fill('Fiat')
    await expect(page).toHaveURL(/q=Fiat/)
    await expect(catalog.getByRole('link', { name: 'Argo', exact: true })).toBeVisible()
    await expect(catalog.getByRole('link', { name: 'Cronos', exact: true })).toBeVisible()
  })
  await test.step('When searching by category and refreshing, then the URL restores the results', async () => {
    await page.getByRole('textbox', { name: 'Search cars' }).fill('SEDAN')
    await expect(page).toHaveURL(/q=SEDAN/)
    await page.reload()
    await expect(page.getByRole('textbox', { name: 'Search cars' })).toHaveValue('SEDAN')
    await expect(catalog.getByRole('link', { name: 'Cronos', exact: true })).toBeVisible()
    await expect(catalog.getByRole('link', { name: 'Argo', exact: true })).toHaveCount(0)
  })
})
