import react from '@vitejs/plugin-react'
import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const { API_TARGET } = loadEnv(mode, process.cwd(), 'API_')


  return {
    plugins: [react()],
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
    },
    server: {
      proxy: {
        '/api': {
          target: API_TARGET || 'http://localhost:8080',
          rewrite: (path) => path.replace(/^\/api/, ''),
        },
      },
    },
  }
})