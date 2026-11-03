import { act, screen } from '@testing-library/react'
import * as ReactDOM from 'react-dom/client'
import { expect, test as scenario, vi } from 'vitest'

vi.mock('react-dom/client', { spy: true })

scenario(
  'Given the document root, when the application starts, then the home menu mounts without fetching either catalog',
  async () => {
    const container = document.createElement('div')
    container.id = 'root'
    document.body.append(container)
    const createRoot = vi.mocked(ReactDOM.createRoot)
    vi.stubGlobal('fetch', vi.fn())

    try {
      await act(async () => {
        await import('./main')
      })

      expect(await screen.findByRole('navigation')).toBeVisible()
      expect(fetch).not.toHaveBeenCalled()
      expect(createRoot).toHaveBeenCalledWith(container)
    } finally {
      const root = createRoot.mock.results[0]?.value
      if (root) act(() => root.unmount())
      vi.unstubAllGlobals()
      container.remove()
    }
  },
)
