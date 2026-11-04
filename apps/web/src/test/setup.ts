import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

import { session } from '../features/auth/session'
Object.defineProperty(navigator, 'locks', {
  configurable: true,
  value: { request: (_name: string, action: () => unknown) => action() },
})
afterEach(() => {
  globalThis.localStorage?.clear()
  cleanup()
  session.set(null)
})
