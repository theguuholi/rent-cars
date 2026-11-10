import { randomUUID } from 'node:crypto'
import { expect, test } from '@playwright/test'

const webBaseUrl = process.env.RENT_CARS_WEB_URL ?? 'http://localhost:5173'

test('Given a visitor, when they complete registration, then they can sign in as a driver', async ({
  page,
}) => {
  const unique = randomUUID().replaceAll('-', '')
  const email = `qa-${unique}@example.test`
  const password = 'qa-driver-password-123'

  await test.step('Given the visitor opens the registration form', async () => {
    await page.goto(`${webBaseUrl}/register`)
    await expect(page.getByRole('heading', { name: 'Create account' })).toBeVisible()
  })

  await test.step('When they complete the driver details and matching passwords', async () => {
    await page.getByLabel('First name').fill('Test')
    await page.getByLabel('Last name').fill('Driver')
    await page.getByLabel('Username').fill(`qa-${unique.slice(0, 24)}`)
    await page.getByLabel('CNH number').fill(
      BigInt(`0x${unique.slice(0, 12)}`)
        .toString()
        .padStart(11, '0')
        .slice(0, 11),
    )
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Password', { exact: true }).fill(password)
    await page.getByLabel('Confirm password').fill(password)
    await page.getByRole('button', { name: 'Create account' }).click()
  })

  await test.step('Then registration succeeds and email/password sign-in works', async () => {
    await expect(page.getByRole('status')).toHaveText('Your account is ready. Sign in to continue.')
    await page.getByRole('link', { name: 'Back to sign in' }).click()
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Password').fill(password)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByRole('button', { name: `Sign out ${email}` })).toBeVisible()
  })
})

test('Given mismatched passwords, when registration is submitted, then the form explains the error without a request', async ({
  page,
}) => {
  await page.goto(`${webBaseUrl}/register`)
  await page.getByLabel('First name').fill('Test')
  await page.getByLabel('Last name').fill('Driver')
  await page.getByLabel('Username').fill(`qa-${randomUUID().slice(0, 20)}`)
  await page.getByLabel('CNH number').fill('12345678901')
  await page.getByLabel('Email').fill(`qa-${randomUUID()}@example.test`)
  await page.getByLabel('Password', { exact: true }).fill('qa-driver-password-123')
  await page.getByLabel('Confirm password').fill('different-password-123')
  const requests: string[] = []
  page.on('request', (request) => {
    if (request.method() === 'POST' && request.url().endsWith('/api/users'))
      requests.push(request.url())
  })
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('alert')).toContainText('Passwords do not match.')
  expect(requests).toHaveLength(0)
})
