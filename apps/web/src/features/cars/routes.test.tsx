import { fireEvent, screen, waitFor } from '@testing-library/react'
import { describe, expect, test as scenario, vi, beforeEach, afterEach } from 'vitest'
import { openCatalogRoute } from '../../test/open-catalog-route'
import { car, categories, specifications } from '../../test/car-fixtures'
import * as api from './cars-api'
import { listCategories } from '../categories/categories-api'
import { listSpecifications } from '../specifications/specifications-api'

vi.mock('./cars-api')
vi.mock('../categories/categories-api')
vi.mock('../specifications/specifications-api')

describe('Car routes', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listCars).mockResolvedValue([
      { ...car, id: 'first', name: 'Civic', available: true },
      car,
    ])
    vi.mocked(api.getCar).mockResolvedValue(car)
    vi.mocked(listCategories).mockResolvedValue(categories)
    vi.mocked(listSpecifications).mockResolvedValue(specifications)
  })
  afterEach(() => vi.unstubAllGlobals())

  scenario(
    'Given the fleet catalog, when navigating through the second record to its edit and delete forms, then each URL retains its identity and cancel leaves it intact',
    async () => {
      const history = openCatalogRoute('/')
      fireEvent.click(await screen.findByRole('link', { name: 'Cars' }))
      await screen.findByText('Civic')
      expect(screen.getByText('Available')).toBeVisible()
      expect(screen.getByText('Unavailable')).toBeVisible()
      fireEvent.click(screen.getByRole('link', { name: 'Corolla' }))
      await screen.findByText('GPS')
      expect(history.location.pathname).toBe('/cars/second')
      expect(screen.getByText('Sedan')).toBeVisible()
      expect(screen.getByText('ABC1234')).toBeVisible()
      fireEvent.click(screen.getByText('Edit car'))
      await screen.findByText('Save car')
      expect(history.location.pathname).toBe('/cars/second/edit')
      fireEvent.click(screen.getByText('Cancel'))
      fireEvent.click(await screen.findByText('Delete car'))
      expect(await screen.findByText('Delete Corolla?')).toBeVisible()
      expect(history.location.pathname).toBe('/cars/second/delete')
      fireEvent.click(screen.getByText('Cancel'))
      fireEvent.click(await screen.findByText('Back to cars'))
      fireEvent.click(await screen.findByText('New car'))
      await screen.findByText('Save car')
      expect(history.location.pathname).toBe('/cars/new')
      fireEvent.click(screen.getByText('Cancel'))
      await screen.findByText('Civic')
      expect(api.deleteCar).not.toHaveBeenCalled()
      expect(api.createCar).not.toHaveBeenCalled()
      expect(api.updateCar).not.toHaveBeenCalled()
    },
  )

  scenario.each([
    ['/cars/second', 'Car details', 'Back to cars'],
    ['/cars/second/edit', 'Edit car', 'Save car'],
    ['/cars/second/delete', 'Delete car', 'Confirm deletion'],
  ])(
    'Given direct URL %s, when opened, then %s targets only the requested record and exposes %s',
    async (path, title, control) => {
      openCatalogRoute(path)
      await screen.findByText(control)
      await waitFor(() => expect(document.title).toBe(title + ' | Rent Cars'))
      expect(api.getCar).toHaveBeenCalledExactlyOnceWith('second', expect.any(AbortSignal))
      expect(api.listCars).not.toHaveBeenCalled()
      expect(api.deleteCar).not.toHaveBeenCalled()
    },
  )
})
