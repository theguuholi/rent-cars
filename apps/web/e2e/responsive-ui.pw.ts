import { expect, test } from '@playwright/test'

const webBaseUrl = process.env.RENT_CARS_WEB_URL ?? 'http://localhost:5173'

for (const width of [390, 1440]) {
  test(`Given a ${width}px viewport, when a guest navigates with the keyboard, then the catalog and sign-in controls remain visible`, async ({
    page,
  }) => {
    await test.step('Given the public catalog at this viewport', async () => {
      await page.setViewportSize({ width, height: 900 })
      await page.goto(`${webBaseUrl}/cars`)
      await expect(page.getByRole('heading', { name: 'Cars', exact: true })).toBeVisible()
    })
    await test.step('When the guest skips the navigation, then focus reaches the main content', async () => {
      await page.keyboard.press('Tab')
      await expect(page.getByRole('link', { name: 'Skip to content' })).toBeFocused()
      await page.keyboard.press('Enter')
      await expect(page.getByRole('main')).toBeFocused()
    })
    await test.step('Then filters have visible boundaries and the page fits the viewport', async () => {
      const name = page.getByRole('textbox', { name: 'Search cars', exact: true })
      await expect(name).toBeVisible()
      await expect(name).toHaveCSS('border-top-width', '1px')
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
        width,
      )
    })
    await test.step('When the guest opens sign in, then both fields and the action are accessible', async () => {
      await page.getByRole('link', { name: 'Sign in', exact: true }).click()
      await expect(page.getByRole('textbox', { name: 'Email' })).toBeVisible()
      await expect(page.getByLabel('Password', { exact: true })).toBeVisible()
      await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeEnabled()
      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
        width,
      )
    })
  })
}

test('Given a long car brand on mobile, when the catalog renders, then its content stays within the viewport', async ({
  page,
  request,
}) => {
  await test.step('Given one existing car with a long brand in the catalog response', async () => {
    const response = await request.get('/cars?size=1')
    expect(response.ok()).toBe(true)
    const fleet = await response.json()
    expect(fleet.content.length).toBeGreaterThan(0)
    const car = { ...fleet.content[0], brand: 'B'.repeat(80) }
    await page.route('**/api/cars?*', (route) =>
      route.fulfill({ json: { ...fleet, content: [car] } }),
    )
    await page.route('**/api/cars/recommendations', (route) => route.fulfill({ json: [] }))
    await page.setViewportSize({ width: 390, height: 844 })
  })
  await test.step('When the visitor reads the card, then the full brand wraps without horizontal scrolling', async () => {
    await page.goto(`${webBaseUrl}/cars`)
    const brand = page.getByText('B'.repeat(80), { exact: true })
    await expect(brand).toBeVisible()
    expect(await brand.evaluate((element) => element.scrollWidth <= element.clientWidth)).toBe(true)
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390)
  })
})

test('Given an administrator on mobile, when opening a user, then all account actions fit the screen', async ({
  page,
}) => {
  await test.step('Given the administrator signs in at 390px', async () => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto(`${webBaseUrl}/login`)
    const email = process.env.RENT_CARS_ADMIN_EMAIL ?? 'test@test'
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Password').fill(process.env.RENT_CARS_ADMIN_PASSWORD ?? 'test@tesT@dev')
    await page.getByRole('button', { name: 'Sign in', exact: true }).click()
    await expect(page.getByRole('button', { name: `Sign out ${email}` })).toBeVisible()
  })
  await test.step('When opening a user from the table, then editing, deletion and navigation remain visible', async () => {
    await page.getByRole('link', { name: 'Users', exact: true }).click()
    await page
      .getByRole('link', { name: /^View / })
      .first()
      .click()
    await expect(page.getByRole('link', { name: 'Edit user' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Delete user' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Back to users' })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390)
  })
})
