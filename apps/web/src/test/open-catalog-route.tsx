import type { QueryClient } from '@tanstack/react-query'
import { createMemoryHistory, RouterProvider } from '@tanstack/react-router'
import { vi } from 'vitest'
import { createAppRouter } from '../router'
import { renderWithQuery } from './render-with-query'

export function openCatalogRoute(path: string, client?: QueryClient) {
  const history = createMemoryHistory({ initialEntries: [path] })
  const router = createAppRouter(history)
  vi.stubGlobal(
    'fetch',
    vi.fn().mockImplementation(async () => Response.json([])),
  )
  renderWithQuery(<RouterProvider router={router} />, client)
  return history
}
