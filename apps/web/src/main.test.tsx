import { QueryClient } from '@tanstack/react-query'
import { retryQuery } from './features/auth/protected-fetch'
import { act, fireEvent, screen } from '@testing-library/react'
import * as ReactDOM from 'react-dom/client'
import { expect, describe, test as scenario, vi } from 'vitest'

vi.mock('react-dom/client', { spy: true })

describe('Application startup', () => {
  scenario(
    'Given session restoration fails at startup, when the visitor retries after the session ends, then the home menu mounts without fetching either catalog',
    async () => {
      const container = document.createElement('div')
      container.id = 'root'
      document.body.append(container)
      const mount = vi.spyOn(QueryClient.prototype, 'mount')
      const createRoot = vi.mocked(ReactDOM.createRoot)
      localStorage.setItem('rent-cars.login', 'device')
      const fetchMock = vi.fn().mockRejectedValue(new Error('Offline'))
      vi.stubGlobal('fetch', fetchMock)
      const listener = vi.spyOn(window, 'addEventListener')

      try {
        await act(async () => {
          await import('./main')
        })

        expect(listener).toHaveBeenCalledWith('storage', expect.any(Function))
        localStorage.clear()
        fireEvent.click(
          await screen.findByRole('button', { name: 'Could not restore your session. Try again' }),
        )
        expect(await screen.findByRole('navigation')).toBeVisible()
        expect((mount.mock.instances[0] as QueryClient).getDefaultOptions().queries?.retry).toBe(
          retryQuery,
        )
        expect(fetchMock).toHaveBeenCalledExactlyOnceWith('/api/csrf')
        expect(createRoot).toHaveBeenCalledWith(container)
      } finally {
        const root = createRoot.mock.results[0]?.value
        if (root) act(() => root.unmount())
        vi.unstubAllGlobals()
        mount.mockRestore()
        listener.mockRestore()
        container.remove()
      }
    },
  )
})
