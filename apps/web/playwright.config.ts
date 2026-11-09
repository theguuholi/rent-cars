import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.pw.ts',
  forbidOnly: Boolean(process.env.CI),
  reporter: 'list',
  use: {
    baseURL: process.env.RENT_CARS_API_URL ?? 'http://localhost:8080',
  },
})
