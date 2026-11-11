import { randomUUID } from 'node:crypto'
import { expect, test } from '@playwright/test'

const webBaseUrl = process.env.RENT_CARS_WEB_URL ?? 'http://localhost:5173'
const adminEmail = process.env.RENT_CARS_ADMIN_EMAIL ?? 'test@test'
const adminPassword = process.env.RENT_CARS_ADMIN_PASSWORD ?? 'test@tesT@dev'

test('Given an administrator, when managing a driver, then the saved profile can be read, updated and removed', async ({
  page,
}) => {
  const suffix = randomUUID().replaceAll('-', '').slice(0, 12)
  const email = `qa-users-${suffix}@example.test`
  const userName = `qa-${suffix}`
  const driveLicense = BigInt(`0x${suffix}`).toString().padStart(11, '0').slice(0, 11)

  await test.step('Given the administrator signs in and opens Users', async () => {
    await page.goto(`${webBaseUrl}/login`)
    await page.getByLabel('Email').fill(adminEmail)
    await page.getByLabel('Password').fill(adminPassword)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await page.getByRole('link', { name: 'Users' }).click()
    await expect(page.getByRole('heading', { name: 'Users' })).toBeVisible()
  })

  await test.step('When the administrator creates a driver, then the account appears in the list', async () => {
    await page.getByRole('link', { name: 'New user' }).click()
    await page.getByLabel('First name').fill('Test')
    await page.getByLabel('Last name').fill('Driver')
    await page.getByLabel('Username').fill(userName)
    await page.getByLabel('CNH number').fill(driveLicense)
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Password', { exact: true }).fill('qa-driver-password-123')
    await page.getByLabel('Confirm password').fill('qa-driver-password-123')
    await page.getByRole('button', { name: 'Create user' }).click()
    await expect(page.getByRole('row', { name: new RegExp(email) })).toBeVisible()
  })

  await test.step('When the administrator opens and edits the profile, then the new name is visible', async () => {
    await page.getByRole('link', { name: `View ${email}` }).click()
    await expect(page.getByText(userName)).toBeVisible()
    await page.getByRole('link', { name: 'Edit user' }).click()
    await page.getByLabel('First name').fill('Updated')
    await page.getByRole('combobox', { name: 'Role' }).selectOption('ADMIN')
    await page.getByRole('button', { name: 'Save user' }).click()
    await expect(page.getByText('Updated Driver')).toBeVisible()
    await expect(page.getByText('ADMIN', { exact: true })).toBeVisible()
  })

  await test.step('Then the administrator deletes the account and it disappears from the list', async () => {
    await page.getByRole('link', { name: 'Delete user' }).click()
    await page.getByRole('button', { name: 'Confirm deletion' }).click()
    await expect(page.getByRole('row', { name: new RegExp(email) })).toHaveCount(0)
  })
})
