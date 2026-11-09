import { expect, test } from '@playwright/test'

const webBaseUrl = process.env.RENT_CARS_WEB_URL ?? 'http://localhost:5173'

test('Given a populated catalog, when a guest opens a car and goes back, then the catalog returns', async ({
  page,
}) => {
  let carName = ''

  await test.step('Given a guest sees a car in the catalog', async () => {
    await page.goto(`${webBaseUrl}/cars`)
    const catalog = page.getByRole('region', { name: 'Cars' })
    await expect(catalog.getByRole('heading', { name: 'Cars' })).toBeVisible()
    const carLink = catalog.getByRole('link').first()
    await expect(carLink).toBeVisible()
    carName = await carLink.innerText()
    expect(carName).not.toBe('')
  })

  await test.step('When the guest opens that car', async () => {
    await page.getByRole('region', { name: 'Cars' }).getByRole('link', { name: carName }).click()
  })

  await test.step('Then the car details and a way back are visible', async () => {
    await expect(page).toHaveURL(/\/cars\/[^/]+$/)
    await expect(page.getByRole('heading', { name: carName, exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Back to cars' })).toBeVisible()
  })

  await test.step('When the guest follows Back to cars', async () => {
    await page.getByRole('link', { name: 'Back to cars' }).click()
  })

  await test.step('Then the catalog shows the same car', async () => {
    await expect(page).toHaveURL(`${webBaseUrl}/cars`)
    await expect(
      page.getByRole('region', { name: 'Cars' }).getByRole('link', { name: carName }),
    ).toBeVisible()
  })
})
