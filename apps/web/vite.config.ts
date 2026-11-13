import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { loadEnv } from 'vite'
import { configDefaults, defineConfig } from 'vitest/config'
import { createApiProxy } from './config/api-proxy.ts'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const { API_TARGET } = loadEnv(mode, process.cwd(), 'API_')

  return {
    plugins: [react(), tailwindcss()],
    test: {
      environment: 'jsdom',
      exclude: [...configDefaults.exclude, '**/.stryker-tmp/**'],
      coverage: {
        provider: 'v8',
        reporter: ['text', 'html', 'lcov'],
        include: ['src/**/*.{ts,tsx}'],
        exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**', 'src/**/*.d.ts'],
        thresholds: { lines: 95, statements: 95, functions: 95, branches: 95 },
      },
      setupFiles: ['./src/test/setup.ts'],
    },
    server: {
      proxy: {
        ...createApiProxy(API_TARGET),
        '/otel': {
          target: 'http://127.0.0.1:4318',
          rewrite: (path) => path.replace(/^\/otel\b/, ''),
        },
      },
    },
  }
})
