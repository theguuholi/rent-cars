import { randomUUID } from 'node:crypto'
import { expect, test } from '@playwright/test'

const webBaseUrl = process.env.RENT_CARS_WEB_URL ?? 'http://localhost:5173'
const driverPassword = 'qa-driver-password-123'

test('Given a new driver and an available car, when the car is rented and returned, then its availability is restored', async ({
  page,
}) => {
  const email = `qa-rental-${randomUUID()}@example.test`
  let carName = ''

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
    await expect(page.getByRole('status')).toHaveText('Your account is ready. Sign in to continue.')
  })

  await test.step('And signs in', async () => {
    await page.getByRole('link', { name: 'Back to sign in' }).click()
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Password').fill(driverPassword)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByRole('button', { name: `Sign out ${email}` })).toBeVisible()
  })

  await test.step('And selects one available car from the catalog', async () => {
    const catalog = page.getByRole('region', { name: 'Cars' })
    const availableCar = catalog
      .getByRole('listitem')
      .filter({ has: page.getByText('Available', { exact: true }) })
      .first()
    await expect(availableCar).toBeVisible()
    const carLink = availableCar.getByRole('link')
    carName = await carLink.innerText()
    await carLink.click()
    await expect(page.getByRole('heading', { name: carName, exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Rent car' })).toBeVisible()
  })

  await test.step('When the driver chooses a return time three days ahead and rents the car', async () => {
    const expectedReturn = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString().slice(0, 16)
    await page.getByLabel('Expected return').fill(expectedReturn)
    await page.getByRole('button', { name: 'Rent car' }).click()
  })

  await test.step('Then the rental is open and the car is unavailable', async () => {
    await expect(page.getByText('Car rented.', { exact: true })).toBeVisible()
    await expect(page.getByText(/Your rental is open\. Expected return:/)).toBeVisible()
    await expect(page.getByRole('button', { name: 'Return car' })).toBeVisible()
    await expect(page.getByText('Unavailable', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Rent car' })).toHaveCount(0)
  })

  await test.step('When the driver returns the car', async () => {
    await page.getByRole('button', { name: 'Return car' }).click()
  })

  await test.step('Then the car is available to rent again', async () => {
    await expect(page.getByText('Car returned.', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Rent car' })).toBeVisible()
    await expect(page.getByText('Available', { exact: true })).toBeVisible()
    await page.getByRole('link', { name: 'Back to cars' }).click()
    const car = page
      .getByRole('listitem')
      .filter({ has: page.getByRole('link', { name: carName }) })
    await expect(car.getByText('Available', { exact: true })).toBeVisible()
  })
})
