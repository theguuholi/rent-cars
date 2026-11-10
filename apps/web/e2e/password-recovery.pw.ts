import { randomUUID } from 'node:crypto'
import { expect, test } from '@playwright/test'

const webBaseUrl = process.env.RENT_CARS_WEB_URL ?? 'http://localhost:5173'
const mailpitUrl = process.env.RENT_CARS_MAILPIT_URL ?? 'http://localhost:8025'

test('Given a driver forgets their password, when they follow the emailed link, then only the new password works', async ({
  page,
  request,
}) => {
  const unique = randomUUID().replaceAll('-', '')
  const email = `reset-${unique}@example.test`
  const firstPassword = 'original-password-123'
  const nextPassword = 'replacement-password-123'
  const cnh = BigInt(`0x${unique.slice(0, 12)}`)
    .toString()
    .padStart(11, '0')
    .slice(0, 11)

  await test.step('Given the driver has registered', async () => {
    await page.goto(`${webBaseUrl}/register`)
    await page.getByLabel('First name').fill('Reset')
    await page.getByLabel('Last name').fill('Driver')
    await page.getByLabel('Username').fill(`reset-${unique.slice(0, 24)}`)
    await page.getByLabel('CNH number').fill(cnh)
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Password', { exact: true }).fill(firstPassword)
    await page.getByLabel('Confirm password').fill(firstPassword)
    await page.getByRole('button', { name: 'Create account' }).click()
    await expect(page.getByRole('status')).toContainText('Your account is ready')
  })

  await test.step('When they request a link, then the page does not reveal whether the account exists', async () => {
    await page.goto(`${webBaseUrl}/login`)
    await page.getByRole('link', { name: 'Forgot password?' }).click()
    await page.getByLabel('Email').fill(email)
    await page.getByRole('button', { name: 'Send reset link' }).click()
    await expect(page.getByRole('status')).toContainText('If the account exists')
  })

  let resetUrl = ''
  await test.step('When they open the link in the local mailbox and reset the password', async () => {
    let messageId = ''
    await expect
      .poll(async () => {
        const response = await request.get(`${mailpitUrl}/api/v1/messages`)
        const inbox = (await response.json()) as {
          messages: { ID: string; To: { Address: string }[] }[]
        }
        messageId =
          inbox.messages.find((message) => message.To.some((to) => to.Address === email))?.ID ?? ''
        return messageId
      })
      .not.toBe('')
    const response = await request.get(`${mailpitUrl}/api/v1/message/${messageId}`)
    const message = (await response.json()) as { Text: string }
    resetUrl =
      message.Text.match(/http:\/\/[^\s]+\/reset-password\?token=[A-Za-z0-9_-]+/)?.[0] ?? ''
    expect(resetUrl).not.toBe('')
    await page.goto(resetUrl)
    await page.getByLabel('New password', { exact: true }).fill(nextPassword)
    await page.getByLabel('Confirm new password').fill(nextPassword)
    await page.getByRole('button', { name: 'Reset password' }).click()
    await expect(page.getByRole('status')).toContainText('Password updated')
  })

  await test.step('Then the new password signs in and the link cannot be reused', async () => {
    await page.getByRole('main').getByRole('link', { name: 'Sign in' }).click()
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Password').fill(firstPassword)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByRole('alert')).toContainText('Invalid email or password')
    await page.getByLabel('Password').fill(nextPassword)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByRole('button', { name: `Sign out ${email}` })).toBeVisible()
    await page.goto(resetUrl)
    await page.getByLabel('New password', { exact: true }).fill('another-password-123')
    await page.getByLabel('Confirm new password').fill('another-password-123')
    await page.getByRole('button', { name: 'Reset password' }).click()
    await expect(page.getByRole('alert')).toContainText('invalid or expired')
  })
})
