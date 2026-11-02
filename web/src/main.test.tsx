import { act, screen } from '@testing-library/react'
import * as ReactDOM from 'react-dom/client'
import { expect, it, vi } from 'vitest'

vi.mock('react-dom/client', { spy: true })

it('mounts the catalog in the document root with its query provider', async () => {
  const container = document.createElement('div')
  container.id = 'root'
  document.body.append(container)
  const createRoot = vi.mocked(ReactDOM.createRoot)
  vi.stubGlobal(
    'fetch',
    vi.fn().mockImplementation(async () => Response.json([{ name: 'VAN', description: null }])),
  )

  try {
    await act(async () => {
      await import('./main')
    })

    expect(await screen.findByText('VAN')).toBeVisible()
    expect(createRoot).toHaveBeenCalledWith(container)
  } finally {
    const root = createRoot.mock.results[0]?.value
    if (root) act(() => root.unmount())
    vi.unstubAllGlobals()
    container.remove()
  }
})
