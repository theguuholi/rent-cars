import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, expect, describe, test as scenario, vi } from 'vitest'
import { openCatalogRoute } from './test/open-catalog-route'

describe('Application layout', () => {
  afterEach(() => vi.unstubAllGlobals())

  scenario(
    'Given an active catalog, when the layout renders, then the banner offers a home link and marks the current catalog',
    async () => {
      openCatalogRoute('/categories')
      await screen.findByText('No vehicle categories available.')
      const nav = screen.getByRole('navigation', { name: 'Vehicle catalogs' })
      expect(screen.getByRole('link', { name: 'Rent Cars home' })).toHaveAttribute('href', '/')
      expect(within(nav).getByRole('link', { name: 'Categories' })).toHaveAttribute(
        'aria-current',
        'page',
      )
      expect(
        within(screen.getByRole('main')).getByRole('heading', { name: 'Vehicle categories' }),
      ).toBeVisible()
    },
  )

  scenario(
    'Given the specifications catalog is active, when the user follows the brand link, then only the home menu remains',
    async () => {
      const history = openCatalogRoute('/specifications')
      await screen.findByText('No specifications yet.')
      const nav = within(screen.getByRole('navigation', { name: 'Vehicle catalogs' }))
      expect(nav.getByRole('link', { name: 'Specifications' })).toHaveAttribute(
        'aria-current',
        'page',
      )
      expect(nav.getByRole('link', { name: 'Categories' })).not.toHaveAttribute('aria-current')
      fireEvent.click(screen.getByRole('link', { name: 'Rent Cars home' }))
      await waitFor(() => expect(screen.getByRole('main')).toBeEmptyDOMElement())
      expect(history.location.pathname).toBe('/')
      expect(fetch).toHaveBeenCalledTimes(1)
    },
  )
})
