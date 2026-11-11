import { QueryClient } from '@tanstack/react-query'
import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, test as scenario, vi } from 'vitest'
import { openCatalogRoute } from '../../../../test/open-catalog-route'
import { car, categories, specifications } from '../../../../test/car-fixtures'
import * as api from '../../cars-api'
import { listCategories } from '../../../categories/categories-api'
import { listSpecifications } from '../../../specifications/specifications-api'

vi.mock('../../cars-api')
vi.mock('../../../categories/categories-api')
vi.mock('../../../specifications/specifications-api')

describe('CarRecord', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listCars).mockResolvedValue({
      content: [],
      number: 0,
      size: 12,
      totalElements: 0,
      totalPages: 0,
    })
    vi.mocked(api.getCar).mockResolvedValue(car)
    vi.mocked(listCategories).mockResolvedValue(categories)
    vi.mocked(listSpecifications).mockResolvedValue(specifications)
  })
  scenario(
    'Given a pending car deletion, when leaving before completion, then the current page stays open',
    async () => {
      let done!: () => void
      vi.mocked(api.deleteCar).mockImplementationOnce(
        () =>
          new Promise((r) => {
            done = r
          }),
      )
      const client = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity } } })
      client.setQueryData(['car', 'first'], { ...car, id: 'first', name: 'Camry' })
      const history = openCatalogRoute('/cars/second/delete', client)
      fireEvent.click(await screen.findByRole('button', { name: 'Confirm deletion' }))
      await waitFor(() => expect(api.deleteCar).toHaveBeenCalled())
      await act(async () => history.push('/cars/first/delete'))
      await screen.findByRole('heading', { name: 'Delete Camry?' })
      await act(async () => done())
      expect(history.location.pathname).toBe('/cars/first/delete')
      expect(client.getQueryData(['car', 'first'])).toMatchObject({ name: 'Camry' })
      expect(client.getQueryData(['car', 'second'])).toBeUndefined()
    },
  )
  afterEach(() => vi.unstubAllGlobals())

  scenario(
    'Given a pending record, when the record is missing and retried, then its details display localized rates and empty associations',
    async () => {
      let reject!: (error: Error) => void
      vi.mocked(api.getCar)
        .mockImplementationOnce(
          () =>
            new Promise((_, failure) => {
              reject = failure
            }),
        )
        .mockResolvedValue({ ...car, available: true, specificationIds: [], specifications: [] })
      openCatalogRoute('/cars/second')
      await screen.findByText('Loading car…')
      await act(async () => reject(new Error('This car no longer exists.')))
      expect(await screen.findByRole('alert')).toHaveTextContent(
        'This car no longer exists. Try again',
      )
      fireEvent.click(screen.getByText('Try again'))
      expect(await screen.findByText('None')).toBeVisible()
      for (const value of ['Sedan', 'Available', 'R$ 150,50', 'R$ 5,00', 'Family sedan', 'Toyota'])
        expect(screen.getByText(value)).toBeVisible()
    },
  )

  scenario(
    'Given a delete confirmation, when the first delete fails and the retry succeeds, then the request cannot be duplicated and the fleet reopens',
    async () => {
      let resolve!: () => void
      vi.mocked(api.deleteCar)
        .mockRejectedValueOnce(new Error('Deletion failed'))
        .mockImplementationOnce(
          () =>
            new Promise((success) => {
              resolve = success
            }),
        )
      const history = openCatalogRoute('/cars/second/delete')
      await screen.findByRole('heading', { name: 'Delete Corolla?' })
      expect(
        screen.getByText('Remove the vehicle with plate ABC1234 from the fleet.'),
      ).toBeVisible()
      expect(screen.getByRole('link', { name: 'Cancel' })).toHaveAttribute('href', '/cars/second')
      fireEvent.click(screen.getByText('Confirm deletion'))
      expect(await screen.findByRole('alert')).toHaveTextContent('Deletion failed')
      fireEvent.click(screen.getByText('Confirm deletion'))
      await waitFor(() => expect(screen.getByText('Confirm deletion')).toBeDisabled())
      expect(screen.getByText('Cancel')).not.toHaveAttribute('href')
      await act(async () => resolve())
      expect(await screen.findByText('No matching cars.')).toBeVisible()
      expect(history.location.pathname).toBe('/cars')
      expect(api.deleteCar).toHaveBeenCalledTimes(2)
      expect(api.deleteCar).toHaveBeenLastCalledWith('second')
    },
  )
})
