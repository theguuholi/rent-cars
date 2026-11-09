import { randomUUID } from 'node:crypto'
import { expect, test, type Page } from '@playwright/test'

const webBaseUrl = process.env.RENT_CARS_WEB_URL ?? 'http://localhost:5173'
const adminEmail = process.env.RENT_CARS_ADMIN_EMAIL ?? 'test@test'
const adminPassword = process.env.RENT_CARS_ADMIN_PASSWORD ?? 'test@tesT@dev'
const driverPassword = 'qa-driver-password-123'

function newDriverEmail() {
  return `qa-${randomUUID()}@example.test`
}

async function registerDriver(page: Page, email: string) {
  await page.goto(`${webBaseUrl}/register`)
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(driverPassword)
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('status')).toHaveText('Your account is ready. Sign in to continue.')
}

async function signIn(page: Page, email: string, password: string) {
  await page.goto(`${webBaseUrl}/login`)
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('button', { name: `Sign out ${email}` })).toBeVisible()
}

test('Given a guest, when they register and sign in, then the driver session survives reload and ends on sign out', async ({
  page,
}) => {
  const email = newDriverEmail()

  await test.step('Given a guest opens registration from sign in', async () => {
    await page.goto(`${webBaseUrl}/login`)
    await page.getByRole('link', { name: 'Create an account' }).click()
    await expect(page).toHaveURL(`${webBaseUrl}/register`)
  })

  await test.step('When the guest registers a driver account', async () => {
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Password').fill(driverPassword)
    await page.getByRole('button', { name: 'Create account' }).click()
    await expect(page.getByRole('status')).toHaveText('Your account is ready. Sign in to continue.')
  })

  await test.step('And signs in with the new account', async () => {
    await page.getByRole('link', { name: 'Back to sign in' }).click()
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Password').fill(driverPassword)
    await page.getByRole('button', { name: 'Sign in' }).click()
  })

  await test.step('Then the driver sees the fleet without admin navigation', async () => {
    await expect(page).toHaveURL(`${webBaseUrl}/cars`)
    await expect(page.getByRole('button', { name: `Sign out ${email}` })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Categories' })).toHaveCount(0)
  })

  await test.step('When the driver reloads, then the session is restored', async () => {
    await page.reload()
    await expect(page.getByRole('button', { name: `Sign out ${email}` })).toBeVisible()
  })

  await test.step('When the driver signs out, then the guest state survives reload', async () => {
    await page.getByRole('button', { name: `Sign out ${email}` }).click()
    await expect(page.getByRole('link', { name: 'Sign in' })).toBeVisible()
    await page.reload()
    await expect(page.getByRole('link', { name: 'Sign in' })).toBeVisible()
  })
})

test('Given an existing driver, when registration reuses the email with different case, then the form explains the conflict', async ({
  page,
}) => {
  const email = newDriverEmail()

  await test.step('Given the driver account already exists', async () => {
    await registerDriver(page, email)
  })

  await test.step('When the guest registers the same email in uppercase', async () => {
    await page.goto(`${webBaseUrl}/register`)
    await page.getByLabel('Email').fill(email.toUpperCase())
    await page.getByLabel('Password').fill(driverPassword)
    await page.getByRole('button', { name: 'Create account' }).click()
  })

  await test.step('Then the conflict is visible and the form values remain', async () => {
    await expect(page.getByRole('alert')).toHaveText('This email is already registered.')
    await expect(page.getByLabel('Email')).toHaveValue(email.toUpperCase())
    await expect(page.getByLabel('Password')).toHaveValue(driverPassword)
  })
})

test('Given invalid admin credentials, when corrected, then sign in succeeds and the error clears', async ({
  page,
}) => {
  await test.step('Given a guest opens sign in', async () => {
    await page.goto(`${webBaseUrl}/login`)
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  })

  await test.step('When the guest submits an incorrect password', async () => {
    await page.getByLabel('Email').fill(adminEmail)
    await page.getByLabel('Password').fill('wrong-password-123')
    await page.getByRole('button', { name: 'Sign in' }).click()
  })

  await test.step('Then an error appears without losing the entered credentials', async () => {
    await expect(page.getByRole('alert')).toHaveText('Invalid email or password.')
    await expect(page.getByLabel('Email')).toHaveValue(adminEmail)
    await expect(page.getByLabel('Password')).toHaveValue('wrong-password-123')
  })

  await test.step('When the password is corrected, then the admin catalogs appear', async () => {
    await page.getByLabel('Password').fill(adminPassword)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByRole('alert')).toHaveCount(0)
    await expect(page.getByRole('button', { name: `Sign out ${adminEmail}` })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Categories' })).toBeVisible()
  })
})

test('Given a guest opens an admin page, when the admin signs in, then the requested page opens and sign out restores the guard', async ({
  page,
}) => {
  await test.step('Given a guest requests the categories page directly', async () => {
    await page.goto(`${webBaseUrl}/categories`)
    await expect(page).toHaveURL(`${webBaseUrl}/login`)
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  })

  await test.step('When the administrator signs in', async () => {
    await page.getByLabel('Email').fill(adminEmail)
    await page.getByLabel('Password').fill(adminPassword)
    await page.getByRole('button', { name: 'Sign in' }).click()
  })

  await test.step('Then the originally requested categories page opens', async () => {
    await expect(page).toHaveURL(`${webBaseUrl}/categories`)
    await expect(page.getByRole('heading', { name: 'Categories' })).toBeVisible()
  })

  await test.step('When the administrator signs out, then the page requires login again', async () => {
    await page.getByRole('button', { name: `Sign out ${adminEmail}` }).click()
    await expect(page.getByRole('link', { name: 'Sign in' })).toBeVisible()
    await page.goto(`${webBaseUrl}/categories`)
    await expect(page).toHaveURL(`${webBaseUrl}/login`)
  })
})

test('Given a signed-in driver, when they open an admin page, then access is denied and the fleet remains available', async ({
  page,
}) => {
  const email = newDriverEmail()

  await test.step('Given a driver registers and signs in', async () => {
    await registerDriver(page, email)
    await signIn(page, email, driverPassword)
  })

  await test.step('When the driver requests an admin page directly', async () => {
    await page.goto(`${webBaseUrl}/categories`)
  })

  await test.step('Then access is denied without exposing the admin catalog', async () => {
    await expect(page).toHaveURL(`${webBaseUrl}/forbidden`)
    await expect(page.getByRole('heading', { name: 'Access denied' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Categories' })).toHaveCount(0)
  })

  await test.step('When the driver returns to cars, then the public catalog opens', async () => {
    await page.getByRole('link', { name: 'Browse cars' }).click()
    await expect(page).toHaveURL(`${webBaseUrl}/cars`)
    await expect(page.getByRole('heading', { name: 'Cars', exact: true })).toBeVisible()
  })
})
