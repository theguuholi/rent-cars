import { randomUUID } from 'node:crypto'
import { expect, test } from '@playwright/test'
import { createRentalCar } from './support/rental-car'

const webBaseUrl = process.env.RENT_CARS_WEB_URL ?? 'http://localhost:5173'
const driverPassword = 'qa-driver-password-123'

for (const committed of [false, true]) {
  test(`Given ${committed ? 'a committed rental whose response is lost' : 'a rejected rental request'}, when the driver checks rental status, then the draft is preserved and the server state determines recovery`, async ({
    page,
    request,
  }) => {
    await page.setViewportSize({ width: committed ? 390 : 1280, height: 900 })
    const email = `qa-rental-${randomUUID()}@example.test`
    const carName = await test.step('Given a dedicated available car exists for this journey', () =>
      createRentalCar(request))

    await test.step('Given a new driver registers', async () => {
      await page.goto(`${webBaseUrl}/register`)
      await page.getByLabel('First name').fill('Test')
      await page.getByLabel('Last name').fill('Driver')
      await page.getByLabel('Username').fill(`qa-${randomUUID().slice(0, 20)}`)
      await page.getByLabel('CNH number').fill(
        BigInt(`0x${randomUUID().replaceAll('-', '').slice(0, 12)}`)
          .toString()
          .padStart(11, '0')
          .slice(0, 11),
      )
      await page.getByLabel('Email').fill(email)
      await page.getByLabel('Password', { exact: true }).fill(driverPassword)
      await page.getByLabel('Confirm password').fill(driverPassword)
      await page.getByRole('button', { name: 'Create account' }).click()
      await expect(page.getByRole('status')).toHaveText(
        'Your account is ready. Sign in to continue.',
      )
    })

    await test.step('And signs in', async () => {
      await page.getByRole('link', { name: 'Back to sign in' }).click()
      await page.getByLabel('Email').fill(email)
      await page.getByLabel('Password').fill(driverPassword)
      await page.getByRole('button', { name: 'Sign in' }).click()
      await expect(page.getByRole('button', { name: `Sign out ${email}` })).toBeVisible()
    })

    await test.step('And selects one available car from the catalog', async () => {
      await page.getByRole('textbox', { name: 'Search cars', exact: true }).fill(carName)
      const catalog = page.getByRole('region', { name: 'Cars', exact: true })
      const carLink = catalog.getByRole('link', { name: carName, exact: true })
      await expect(carLink).toBeVisible()
      await carLink.click()
      await expect(page.getByRole('heading', { name: carName, exact: true })).toBeVisible()
      await expect(page.getByRole('button', { name: 'Rent car' })).toBeVisible()
    })

    const expectedReturn = new Date(Date.now() + 3 * 86400000).toISOString().slice(0, 16)
    let writes = 0
    await page.route('**/api/rentals', async (route) => {
      if (route.request().method() !== 'POST') return route.continue()
      writes++
      if (committed) {
        const response = await route.fetch()
        expect(response.status()).toBe(201)
      }
      await route.fulfill({ status: 500, contentType: 'application/json', body: '{}' })
    })

    await test.step('When the rental response fails', async () => {
      await page.getByLabel('Expected return').fill(expectedReturn)
      await page.getByRole('button', { name: 'Rent car', exact: true }).click()
      await expect(
        page.getByText('Your return date is kept. Check rental status before trying again.'),
      ).toBeVisible()
      await expect(page.getByLabel('Expected return')).toHaveValue(expectedReturn)
      await expect(page.getByText('Car rented.', { exact: true })).toHaveCount(0)
      expect(writes).toBe(1)
    })

    await test.step('Then checking status reads the server without creating another rental', async () => {
      await page.getByRole('button', { name: 'Check rental status', exact: true }).focus()
      await page.keyboard.press('Enter')
      await expect(page.getByText('Rental status updated.', { exact: true })).toBeVisible()
      expect(writes).toBe(1)
      await page.unroute('**/api/rentals')
      if (committed) {
        await expect(page.getByRole('button', { name: 'Return car', exact: true })).toBeVisible()
        await expect(page.getByRole('button', { name: 'Rent car', exact: true })).toHaveCount(0)
      } else {
        await expect(page.getByLabel('Expected return')).toHaveValue(expectedReturn)
        await expect(page.getByText('Available', { exact: true })).toBeVisible()
        await page.getByRole('button', { name: 'Rent car', exact: true }).click()
        await expect(page.getByText('Car rented.', { exact: true })).toBeVisible()
      }
    })

    await test.step('And returning the car restores its availability', async () => {
      await page.getByRole('button', { name: 'Return car', exact: true }).click()
      await expect(page.getByText('Car returned.', { exact: true })).toBeVisible()
      await expect(page.getByText('Available', { exact: true })).toBeVisible()
    })
  })
}
