import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.pw.ts',
  forbidOnly: Boolean(process.env.CI),
  workers: process.env.CI ? 2 : undefined,
  reporter: [['list', { printSteps: true }]],
  use: {
    baseURL: process.env.RENT_CARS_API_URL ?? 'http://localhost:8080',
  },
})
