import { act, fireEvent, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, test as scenario, vi } from 'vitest'
import { QueryClient } from '@tanstack/react-query'
import { openCatalogRoute } from '../../../../test/open-catalog-route'
import { car } from '../../../../test/car-fixtures'
import { listRecommendedCars } from '../../recommendations-api'

vi.mock('../../recommendations-api', () => ({ listRecommendedCars: vi.fn() }))

function recommendations() {
  return within(screen.getByRole('region', { name: 'Most rented cars' }))
}

describe('MostRentedCars', () => {
  beforeEach(() => vi.resetAllMocks())
  afterEach(() => vi.unstubAllGlobals())

  scenario(
    'Given a pending ranking, when a visitor opens the fleet, then loading is announced and catalog filters remain available',
    async () => {
      vi.mocked(listRecommendedCars).mockImplementation(() => new Promise(() => {}))
      openCatalogRoute('/cars', undefined, null)
      await screen.findByRole('region', { name: 'Most rented cars' })
      expect(recommendations().getByRole('status')).toHaveTextContent('Loading recommendations…')
      expect(screen.getByRole('textbox', { name: 'Name' })).toBeEnabled()
      expect(listRecommendedCars).toHaveBeenCalledWith(expect.any(AbortSignal))
    },
  )

  scenario(
    'Given no ranking, when a visitor opens the fleet, then the empty state directs them to the catalog',
    async () => {
      vi.mocked(listRecommendedCars).mockResolvedValue([])
      openCatalogRoute('/cars')
      await screen.findByText('No recommendations yet. Browse the fleet below.')
      expect(recommendations().queryByRole('list')).not.toBeInTheDocument()
      expect(recommendations().getByRole('heading', { level: 2 })).toHaveTextContent(
        'Most rented cars',
      )
      expect(
        recommendations().getByText(
          'Available cars ranked by rentals in the latest 30-day snapshot.',
        ),
      ).toBeVisible()
    },
  )

  scenario(
    'Given ranked cars, when the section renders, then rank order, current prices and individual detail URLs are preserved',
    async () => {
      vi.mocked(listRecommendedCars).mockResolvedValue([
        { ...car, id: 'first', name: 'Zebra', brand: 'Honda', dailyRateCents: 12345 },
        { ...car, id: 'second', name: 'Alpha', brand: 'Toyota', dailyRateCents: 5000 },
      ])
      openCatalogRoute('/cars', undefined, null)
      await screen.findByRole('link', { name: 'Zebra' })
      const rows = recommendations().getAllByRole('listitem')
      expect(rows).toHaveLength(2)
      expect(rows[0]).toHaveTextContent('Honda')
      expect(rows[0]).toHaveTextContent('Zebra')
      expect(rows[0]).toHaveTextContent('R$ 123,45 per day')
      expect(rows[1]).toHaveTextContent('Toyota')
      expect(rows[1]).toHaveTextContent('Alpha')
      expect(rows[1]).toHaveTextContent('R$ 50,00 per day')
      expect(within(rows[0]).getByRole('link')).toHaveAttribute('href', '/cars/first')
      expect(within(rows[1]).getByRole('link')).toHaveAttribute('href', '/cars/second')
    },
  )

  scenario(
    'Given a failed ranking, when the visitor retries, then a recovered ranking replaces the error without automatic retries',
    async () => {
      vi.mocked(listRecommendedCars)
        .mockRejectedValueOnce(new Error('Offline'))
        .mockResolvedValue([])
      openCatalogRoute(
        '/cars',
        new QueryClient({ defaultOptions: { queries: { retry: 2, retryDelay: 0 } } }),
      )
      await screen.findByRole('alert')
      expect(recommendations().getByRole('alert')).toHaveTextContent(
        'Could not load recommendations. Try again',
      )
      await act(async () => {
        await new Promise((resolve) => setTimeout(resolve, 20))
      })
      expect(listRecommendedCars).toHaveBeenCalledTimes(1)
      fireEvent.click(recommendations().getByRole('button', { name: 'Try again' }))
      await screen.findByText('No recommendations yet. Browse the fleet below.')
      expect(recommendations().queryByRole('alert')).not.toBeInTheDocument()
      expect(listRecommendedCars).toHaveBeenCalledTimes(2)
    },
  )

  scenario(
    'Given cached popularity, when car queries are invalidated after a rental, then the ranking is fetched again',
    async () => {
      vi.mocked(listRecommendedCars).mockResolvedValue([])
      const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
      openCatalogRoute('/cars', client)
      await screen.findByText('No recommendations yet. Browse the fleet below.')
      expect(client.getQueryData(['cars', 'recommendations'])).toEqual([])
      await act(async () => {
        await client.invalidateQueries({ queryKey: ['cars'] })
      })
      expect(listRecommendedCars).toHaveBeenCalledTimes(2)
    },
  )
})
