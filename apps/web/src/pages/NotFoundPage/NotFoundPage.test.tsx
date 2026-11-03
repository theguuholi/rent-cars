import { fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, describe, test as scenario, vi } from 'vitest'
import { openCatalogRoute } from '../../test/open-catalog-route'

describe('NotFoundPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  scenario.each(['/category', '/specification', '/categories/unknown', '/specifications/unknown'])(
    'Given an unknown URL %s, when the visitor follows the 404 page’s home link, then the menu opens without fetching a catalog',
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
})
