import { randomUUID } from 'node:crypto'
import { expect, test, type Page } from '@playwright/test'

const webBaseUrl = process.env.RENT_CARS_WEB_URL ?? 'http://localhost:5173'
const adminEmail = process.env.RENT_CARS_ADMIN_EMAIL ?? 'test@test'
const adminPassword = process.env.RENT_CARS_ADMIN_PASSWORD ?? 'test@tesT@dev'

async function signInAsAdmin(page: Page) {
  await page.goto(`${webBaseUrl}/login`)
  await page.getByLabel('Email').fill(adminEmail)
  await page.getByLabel('Password').fill(adminPassword)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('button', { name: `Sign out ${adminEmail}` })).toBeVisible()
}

test('Given an admin, when a specification form is corrected, then it saves and can be removed', async ({
  page,
}) => {
  const name = `QA-ZOD-${randomUUID().slice(0, 8).toUpperCase()}`

  await test.step('Given the administrator opens a new specification', async () => {
    await signInAsAdmin(page)
    await page.goto(`${webBaseUrl}/specifications/new`)
    await expect(page.getByRole('group', { name: 'New specification' })).toBeVisible()
  })

  await test.step('When the blank form is submitted, then both required fields explain their errors', async () => {
    await page.getByRole('button', { name: 'Save specification' }).click()
    await expect(page.getByLabel('Name')).toHaveAccessibleDescription('Enter a name.')
    await expect(page.getByLabel('Description')).toHaveAccessibleDescription('Enter a description.')
  })

  await test.step('When the fields are corrected, then the specification appears in the catalog', async () => {
    await page.getByLabel('Name').fill(` ${name} `)
    await page.getByLabel('Description').fill(' QA equipment ')
    await page.getByRole('button', { name: 'Save specification' }).click()
    await expect(page.getByRole('status')).toHaveText('Specification saved.')
    await expect(page.getByRole('row', { name: new RegExp(name) })).toBeVisible()
  })

  await test.step('Then the administrator removes the test specification', async () => {
    await page.getByRole('link', { name: `Delete ${name}` }).click()
    await page.getByRole('button', { name: 'Confirm deletion' }).click()
    await expect(page.getByRole('status')).toHaveText('Specification deleted.')
  })
})

test('Given an admin, when a car form is corrected, then it saves and can be removed', async ({
  page,
}) => {
  const name = `QA Zod ${randomUUID().slice(0, 8)}`
  const plate = `Z${randomUUID().replaceAll('-', '').slice(0, 6).toUpperCase()}`

  await test.step('Given the administrator opens a new car', async () => {
    await signInAsAdmin(page)
    await page.goto(`${webBaseUrl}/cars/new`)
    await expect(page.getByRole('heading', { name: 'New car' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Save car' })).toBeVisible()
  })

  await test.step('When the blank form is submitted, then required fields explain their errors', async () => {
    await page.getByRole('button', { name: 'Save car' }).click()
    await expect(page.getByLabel('Name')).toHaveAccessibleDescription('Enter a name.')
    await expect(page.getByLabel('Category')).toHaveAccessibleDescription('Select a category.')
    await expect(page.getByLabel('Daily rate (BRL)')).toHaveAccessibleDescription(
      'Enter a nonnegative BRL amount with up to two decimal places.',
    )
  })

  await test.step('When the fields are corrected, then the car details show normalized values', async () => {
    await page.getByLabel('Name').fill(` ${name} `)
    await page.getByLabel('Description').fill(' QA car ')
    await page.getByLabel('Brand').fill(' QA brand ')
    await page.getByLabel('License plate').fill(` ${plate.toLowerCase()} `)
    await page.getByLabel('Daily rate (BRL)').fill('150,50')
    await page.getByLabel('Fine amount (BRL)').fill('5,00')
    await page.getByLabel('Category').selectOption({ index: 1 })
    await page.getByRole('button', { name: 'Save car' }).click()
    await expect(page.getByRole('heading', { name, exact: true })).toBeVisible()
    await expect(page.getByText(plate, { exact: false })).toBeVisible()
  })

  await test.step('Then the administrator removes the test car', async () => {
    await page.getByRole('link', { name: 'Delete car' }).click()
    await page.getByRole('button', { name: 'Confirm deletion' }).click()
    await expect(page).toHaveURL(`${webBaseUrl}/cars`)
  })
})
