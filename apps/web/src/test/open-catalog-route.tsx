import { session, type Session } from '../features/auth/session'
import { QueryClient } from '@tanstack/react-query'
import { createMemoryHistory, RouterProvider } from '@tanstack/react-router'
import { vi } from 'vitest'
import { createAppRouter } from '../router'
import { renderWithQuery } from './render-with-query'

export const adminSession: Session = {
  accessToken: 'test-admin',
  user: { id: 'admin', email: 'admin@example.test', role: 'ADMIN' },
}
export function openCatalogRoute(
  path: string,
  client?: QueryClient,
  identity: Session | null = adminSession,
) {
  session.set(identity)
  const history = createMemoryHistory({ initialEntries: [path] })
  const router = createAppRouter(history)
  vi.stubGlobal(
    'fetch',
    vi.fn().mockImplementation(async () => Response.json([])),
  )
  renderWithQuery(<RouterProvider router={router} />, client)
  return history
}
