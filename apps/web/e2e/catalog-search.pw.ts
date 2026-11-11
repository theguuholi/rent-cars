import { expect, test } from '@playwright/test'

const webBaseUrl = process.env.RENT_CARS_WEB_URL ?? 'http://localhost:5173'

test('Given the public catalog, when a guest filters and changes pages, then the selected search survives navigation', async ({
  page,
}) => {
  await test.step('Given a guest opens the catalog', async () => {
    await page.goto(`${webBaseUrl}/cars`)
    await expect(page.getByRole('heading', { name: 'Cars', exact: true })).toBeVisible()
  })

  await test.step('When the guest searches for Fiat cars one at a time', async () => {
    await page.getByRole('textbox', { name: 'Brand' }).fill('Fiat')
    await page.getByRole('combobox', { name: 'Cars per page' }).selectOption('1')
    await expect(page).toHaveURL(/brand=Fiat/)
    await expect(page.getByRole('link', { name: 'Argo' })).toBeVisible()
    await page.getByRole('button', { name: 'Next page' }).click()
  })

  await test.step('Then the next matching car is shown and browser history restores the first page', async () => {
    await expect(page.getByRole('link', { name: 'Cronos' })).toBeVisible()
    await page.goBack()
    await expect(page.getByRole('link', { name: 'Argo' })).toBeVisible()
  })

  await test.step('When category and name narrow the fleet, then the result and empty state follow the filters', async () => {
    await page.getByRole('textbox', { name: 'Category' }).fill('SEDAN')
    await expect(page.getByRole('link', { name: 'Cronos' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Argo' })).toHaveCount(0)
    await page.getByRole('textbox', { name: 'Name' }).fill('No such model')
    await expect(page.getByText('No matching cars.')).toBeVisible()
    await page.getByRole('textbox', { name: 'Name' }).fill('Cronos')
    await expect(page.getByRole('link', { name: 'Cronos' })).toBeVisible()
  })
})
