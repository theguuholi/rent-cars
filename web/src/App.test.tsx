import { screen, within } from '@testing-library/react'
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
})
