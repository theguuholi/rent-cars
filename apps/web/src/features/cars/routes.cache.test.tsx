import { QueryClient } from '@tanstack/react-query'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, test as scenario, vi } from 'vitest'
import { car, categories, specifications } from '../../test/car-fixtures'
import { openCatalogRoute } from '../../test/open-catalog-route'
import { listCategories } from '../categories/categories-api'
import { listSpecifications } from '../specifications/specifications-api'
import * as api from './cars-api'

vi.mock('./cars-api')
vi.mock('../categories/categories-api')
vi.mock('../specifications/specifications-api')

function cachedFleet() {
  const client = new QueryClient({
    defaultOptions: { queries: { staleTime: Infinity, retry: false } },
  })
  client.setQueryData(['cars'], [car])
  client.setQueryData(['car', car.id], car)
  client.setQueryData(['categories'], categories)
  client.setQueryData(['specifications'], specifications)
  return client
}

describe('Fleet navigation with fresh cached records', () => {
  afterEach(() => {
    vi.resetAllMocks()
    vi.unstubAllGlobals()
  })

  scenario(
    'Given a cached car and catalogs, when an edit succeeds, then the saved detail replaces the form and the complete detail and fleet list reload',
    async () => {
      const client = cachedFleet()
      const saved = { ...car, name: 'Updated Corolla' }
      vi.mocked(api.updateCar).mockResolvedValue(saved)
      vi.mocked(api.getCar).mockResolvedValue(saved)
      vi.mocked(api.listCars).mockResolvedValue([saved])
      const history = openCatalogRoute('/cars', client)

      fireEvent.click(await screen.findByRole('link', { name: car.name }))
      fireEvent.click(await screen.findByRole('link', { name: 'Edit car' }))
      fireEvent.change(await screen.findByLabelText('Name'), { target: { value: saved.name } })
      fireEvent.click(screen.getByRole('button', { name: 'Save car' }))

      expect(await screen.findByRole('heading', { name: saved.name })).toBeVisible()
      expect(history.length).toBe(3)
      expect(await screen.findByText('GPS')).toBeVisible()
      expect(listCategories).not.toHaveBeenCalled()
      expect(listSpecifications).not.toHaveBeenCalled()
      expect(api.getCar).toHaveBeenCalledExactlyOnceWith(car.id, expect.any(AbortSignal))
      expect(api.listCars).not.toHaveBeenCalled()
      fireEvent.click(screen.getByRole('link', { name: 'Back to cars' }))
      expect(await screen.findByRole('link', { name: saved.name })).toBeVisible()
      expect(api.listCars).toHaveBeenCalledTimes(1)
    },
  )

  scenario(
    'Given multiple cached records, when one is deleted, then the refreshed fleet replaces confirmation and only that exact record is evicted',
    async () => {
      const client = cachedFleet()
      const other = { ...car, id: 'first', name: 'Camry' }
      client.setQueryData(['car', other.id], other)
      const childKey = ['car', car.id, 'rental-history']
      client.setQueryData(childKey, ['prior rental'])
      vi.mocked(api.deleteCar).mockResolvedValue(undefined)
      vi.mocked(api.listCars).mockResolvedValue([other])
      const history = openCatalogRoute('/cars/' + car.id + '/delete', client)

      fireEvent.click(await screen.findByRole('button', { name: 'Confirm deletion' }))
      const remaining = await screen.findByRole('link', { name: other.name })
      expect(history.length).toBe(1)
      expect(api.listCars).toHaveBeenCalledTimes(1)
      await waitFor(() => expect(client.getQueryState(['car', car.id])).toBeUndefined())
      expect(client.getQueryData(childKey)).toEqual(['prior rental'])
      fireEvent.click(remaining)
      expect(await screen.findByRole('heading', { name: other.name })).toBeVisible()
      expect(await screen.findByText('GPS')).toBeVisible()
      expect(api.getCar).not.toHaveBeenCalled()
      expect(listCategories).not.toHaveBeenCalled()
      expect(listSpecifications).not.toHaveBeenCalled()
    },
  )
})
