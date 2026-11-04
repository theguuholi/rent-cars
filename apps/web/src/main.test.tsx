import { QueryClient } from '@tanstack/react-query'
import { retryQuery } from './features/auth/protected-fetch'
import { act, screen } from '@testing-library/react'
import * as ReactDOM from 'react-dom/client'
import { expect, describe, test as scenario, vi } from 'vitest'

vi.mock('react-dom/client', { spy: true })

describe('Application startup', () => {
  scenario(
    'Given the document root, when the application starts, then the home menu mounts without fetching either catalog',
    async () => {
      const container = document.createElement('div')
      container.id = 'root'
      document.body.append(container)
      const mount = vi.spyOn(QueryClient.prototype, 'mount')
      const createRoot = vi.mocked(ReactDOM.createRoot)
      vi.stubGlobal('fetch', vi.fn())

      try {
        await act(async () => {
          await import('./main')
        })

        expect(await screen.findByRole('navigation')).toBeVisible()
        expect((mount.mock.instances[0] as QueryClient).getDefaultOptions().queries?.retry).toBe(
          retryQuery,
        )
        expect(fetch).not.toHaveBeenCalled()
        expect(createRoot).toHaveBeenCalledWith(container)
      } finally {
        const root = createRoot.mock.results[0]?.value
        if (root) act(() => root.unmount())
        vi.unstubAllGlobals()
        mount.mockRestore()
        container.remove()
      }
    },
  )
})
