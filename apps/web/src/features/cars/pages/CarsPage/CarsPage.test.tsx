import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, test as scenario, vi } from 'vitest'
import { openCatalogRoute } from '../../../../test/open-catalog-route'
import * as api from '../../cars-api'
import { car } from '../../../../test/car-fixtures'

vi.mock('../../cars-api')

describe('CarsPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listCars).mockResolvedValue({
      content: [],
      number: 0,
      size: 12,
      totalElements: 0,
      totalPages: 0,
    })
  })
  afterEach(() => vi.unstubAllGlobals())

  scenario(
    'Given invalid catalog URL options, when opening the page, then safe defaults are sent to the API',
    async () => {
      openCatalogRoute('/cars?page=-1&size=101&sort=price,asc')
      await screen.findByText('No matching cars.')
      expect(api.listCars).toHaveBeenCalledWith(
        { name: '', brand: '', category: '', page: 0, size: 12, sort: 'name,asc' },
        true,
        expect.any(AbortSignal),
      )
    },
  )

  scenario(
    'Given a pending fleet request, when it fails and the visitor retries, then an empty fleet offers registration',
    async () => {
      let reject!: (error: Error) => void
      vi.mocked(api.listCars).mockImplementationOnce(
        () =>
          new Promise((_, failure) => {
            reject = failure
          }),
      )
      openCatalogRoute('/cars')
      await screen.findByText('Loading cars…')
      await act(async () => reject(new Error('Offline')))
      expect(await screen.findByRole('alert')).toHaveTextContent('Offline Try again')
      fireEvent.click(screen.getByText('Try again'))
      expect(await screen.findByText('No matching cars.')).toBeVisible()
      expect(screen.getByRole('link', { name: 'New car' })).toHaveAttribute('href', '/cars/new')
    },
  )

  scenario(
    'Given two matching cars, when changing pages and filters, then the URL and server request follow the selected catalog state',
    async () => {
      vi.mocked(api.listCars).mockImplementation(async (search) => ({
        content: [{ ...car, name: search.page === 0 ? 'Argo' : 'Cronos' }],
        number: search.page,
        size: search.size,
        totalElements: 2,
        totalPages: 2,
      }))
      const history = openCatalogRoute('/cars?brand=Fiat&size=1')
      expect(await screen.findByRole('link', { name: 'Argo' })).toBeVisible()
      expect(screen.getByRole('textbox', { name: 'Brand' })).toHaveValue('Fiat')
      fireEvent.click(screen.getByRole('button', { name: 'Next page' }))
      expect(await screen.findByRole('link', { name: 'Cronos' })).toBeVisible()
      expect(new URLSearchParams(history.location.search).get('page')).toBe('1')
      fireEvent.click(screen.getByRole('button', { name: 'Previous page' }))
      await screen.findByRole('link', { name: 'Argo' })
      await waitFor(() =>
        expect(new URLSearchParams(history.location.search).get('page')).toBe('0'),
      )
      fireEvent.change(screen.getByRole('textbox', { name: 'Brand' }), {
        target: { value: 'Honda' },
      })
      await waitFor(() =>
        expect(new URLSearchParams(history.location.search).get('brand')).toBe('Honda'),
      )
      fireEvent.change(screen.getByRole('textbox', { name: 'Category' }), {
        target: { value: 'SEDAN' },
      })
      await waitFor(() =>
        expect(new URLSearchParams(history.location.search).get('category')).toBe('SEDAN'),
      )
      fireEvent.change(screen.getByRole('combobox', { name: 'Sort by' }), {
        target: { value: 'brand,desc' },
      })
      await waitFor(() =>
        expect(new URLSearchParams(history.location.search).get('sort')).toBe('brand,desc'),
      )
      fireEvent.change(screen.getByRole('combobox', { name: 'Cars per page' }), {
        target: { value: '6' },
      })
      await waitFor(() =>
        expect(new URLSearchParams(history.location.search).get('size')).toBe('6'),
      )
      fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: 'Argo' } })
      await waitFor(() =>
        expect(new URLSearchParams(history.location.search).get('page')).toBe('0'),
      )
      await screen.findByRole('link', { name: 'Argo' })
      expect(api.listCars).toHaveBeenLastCalledWith(
        expect.objectContaining({
          name: 'Argo',
          brand: 'Honda',
          category: 'SEDAN',
          sort: 'brand,desc',
          page: 0,
          size: 6,
        }),
        true,
        expect.any(AbortSignal),
      )
      expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled()
    },
  )
})
