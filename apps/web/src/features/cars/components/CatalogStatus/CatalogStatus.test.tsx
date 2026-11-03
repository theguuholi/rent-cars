import { fireEvent, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, test as scenario, vi } from 'vitest'
import { openCatalogRoute } from '../../../../test/open-catalog-route'
import { categories, specifications } from '../../../../test/car-fixtures'
import { listCategories } from '../../../categories/categories-api'
import { listSpecifications } from '../../../specifications/specifications-api'

vi.mock('../../../categories/categories-api')
vi.mock('../../../specifications/specifications-api')

describe('CatalogStatus', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(listCategories).mockResolvedValue(categories)
    vi.mocked(listSpecifications).mockResolvedValue(specifications)
  })
  afterEach(() => vi.unstubAllGlobals())

  scenario.each(['categories', 'specifications'])(
    'Given a failed %s lookup, when retrying, then the form offers current choices',
    async (kind) => {
      if (kind === 'categories')
        vi.mocked(listCategories).mockRejectedValueOnce(new Error('offline'))
      else vi.mocked(listSpecifications).mockRejectedValueOnce(new Error('offline'))
      openCatalogRoute('/cars/new')
      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Could not load vehicle catalogs. Try again',
      )
      fireEvent.click(screen.getByText('Try again'))
      expect(await screen.findByLabelText('GPS')).toBeVisible()
      expect(screen.getByRole('option', { name: 'Sedan' })).toBeInTheDocument()
      expect(listCategories).toHaveBeenCalledTimes(2)
      expect(listSpecifications).toHaveBeenCalledTimes(2)
    },
  )
})
