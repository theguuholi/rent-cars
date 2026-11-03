import { screen, within } from '@testing-library/react'
import { expect, describe, test as scenario, vi } from 'vitest'
import { renderWithQuery } from '../../test/render-with-query'
import { CategoriesPage } from './CategoriesPage'
import { listCategories } from './categories-api'

vi.mock('./categories-api', () => ({ listCategories: vi.fn() }))

describe('CategoriesPage', () => {
  scenario(
    'Given a catalog containing COUPE, when CategoriesPage renders, then COUPE appears in the labeled categories region',
    async () => {
      vi.mocked(listCategories).mockResolvedValue([{ name: 'COUPE', description: 'Two doors' }])
      renderWithQuery(<CategoriesPage />)
      const region = within(screen.getByRole('region', { name: 'Vehicle categories' }))
      expect(region.getByRole('heading', { level: 2 })).toHaveTextContent('Vehicle categories')
      const catalog = within(await region.findByRole('list', { name: 'Vehicle categories' }))
      expect(catalog.getByRole('listitem')).toHaveTextContent('COUPE')
    },
  )
})
