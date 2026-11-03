import { screen } from '@testing-library/react'
import { afterEach, expect, test as scenario, vi } from 'vitest'
import { openCatalogRoute } from '../test/open-catalog-route'

afterEach(() => vi.unstubAllGlobals())

scenario(
  'Given the home URL, when the page opens, then the menu appears without requesting a catalog',
  async () => {
    openCatalogRoute('/')
    expect(await screen.findByRole('navigation')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Categories' })).toHaveAttribute('href', '/categories')
    expect(screen.getByRole('link', { name: 'Specifications' })).toHaveAttribute(
      'href',
      '/specifications',
    )
    expect(screen.queryByRole('heading', { level: 2 })).not.toBeInTheDocument()
    expect(fetch).not.toHaveBeenCalled()
  },
)
