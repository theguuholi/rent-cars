import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

import { session } from '../features/auth/session'
afterEach(() => {
  cleanup()
  session.set(null)
})
