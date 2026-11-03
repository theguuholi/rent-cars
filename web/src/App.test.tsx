import { screen, within } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { openCatalogRoute } from './test/open-catalog-route'

afterEach(() => vi.unstubAllGlobals())

it('keeps navigation available above the active catalog and links the brand to home', async () => {
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
})
