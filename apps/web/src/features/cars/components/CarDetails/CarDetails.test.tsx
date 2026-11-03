import { act, fireEvent, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, test as scenario, vi } from 'vitest'
import { openCatalogRoute } from '../../../../test/open-catalog-route'
import { car, categories, specifications } from '../../../../test/car-fixtures'
import * as api from '../../cars-api'
import { listCategories } from '../../../categories/categories-api'
import { listSpecifications } from '../../../specifications/specifications-api'

vi.mock('../../cars-api')
vi.mock('../../../categories/categories-api')
vi.mock('../../../specifications/specifications-api')

describe('CarDetails', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getCar).mockResolvedValue(car)
    vi.mocked(listCategories).mockResolvedValue(categories)
    vi.mocked(listSpecifications).mockResolvedValue(specifications)
  })
  afterEach(() => vi.unstubAllGlobals())

  scenario(
    'Given a car with several specifications, when its catalogs finish loading, then details resolve only its selected entries in order',
    async () => {
      let resolve!: (items: typeof specifications) => void
      vi.mocked(api.getCar).mockResolvedValue({ ...car, specificationIds: ['air', 'gps'] })
      vi.mocked(listCategories).mockResolvedValue([
        { ...categories[0], id: 'van', name: 'Van' },
        ...categories,
      ])
      vi.mocked(listSpecifications).mockImplementationOnce(
        () =>
          new Promise((success) => {
            resolve = success
          }),
      )
      openCatalogRoute('/cars/second')
      expect(await screen.findByText('Loading vehicle catalogs…')).toBeVisible()
      expect(screen.getByText('Unavailable')).toBeVisible()
      await act(async () =>
        resolve([
          ...specifications,
          { id: 'air', name: 'Air conditioning', description: 'Climate control' },
        ]),
      )
      expect(await screen.findByText('Air conditioning, GPS')).toBeVisible()
      expect(screen.getByText('Sedan')).toBeVisible()
      expect(screen.getByRole('link', { name: 'Edit car' })).toHaveAttribute(
        'href',
        '/cars/second/edit',
      )
      expect(screen.getByRole('link', { name: 'Delete car' })).toHaveAttribute(
        'href',
        '/cars/second/delete',
      )
    },
  )

  scenario(
    'Given a failed specifications lookup, when opening car details, then the record remains visible and the catalog offers a retry',
    async () => {
      vi.mocked(listSpecifications).mockRejectedValueOnce(new Error('Offline'))
      openCatalogRoute('/cars/second')
      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Could not load vehicle catalogs. Try again',
      )
      expect(screen.getByRole('heading', { name: 'Corolla' })).toBeVisible()
      fireEvent.click(screen.getByText('Try again'))
      expect(await screen.findByText('GPS')).toBeVisible()
    },
  )

  scenario(
    'Given catalog entries missing from lookup results, when viewing the saved car, then it identifies those associations by ID',
    async () => {
      vi.mocked(listCategories).mockResolvedValue([])
      vi.mocked(listSpecifications).mockResolvedValue([])
      openCatalogRoute('/cars/second')
      expect(await screen.findByText('cat')).toBeVisible()
      expect(screen.getByText('gps')).toBeVisible()
    },
  )
})
