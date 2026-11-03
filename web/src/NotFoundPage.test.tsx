import { fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { openCatalogRoute } from './test/open-catalog-route'

afterEach(() => vi.unstubAllGlobals())

it.each(['/category', '/specification', '/categories/unknown', '/specifications/unknown'])(
  'offers a way home from %s without fetching a catalog',
  async (path) => {
    const history = openCatalogRoute(path)
    const link = await screen.findByRole('link', { name: 'Back to home' })
    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeVisible()
    fireEvent.click(link)
    await waitFor(() =>
      expect(screen.queryByRole('link', { name: 'Back to home' })).not.toBeInTheDocument(),
    )
    expect(history.location.pathname).toBe('/')
    expect(screen.getByRole('navigation')).toBeVisible()
    expect(fetch).not.toHaveBeenCalled()
  },
)
