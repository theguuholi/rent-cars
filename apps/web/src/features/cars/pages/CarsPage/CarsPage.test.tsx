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
        { q: '', name: '', brand: '', category: '', page: 0, size: 12, sort: 'name,asc' },
        true,
        expect.any(AbortSignal),
      )
    },
  )

  scenario(
    'Given the catalog URL without filters, when opening it, then the URL stays clean',
    async () => {
      const history = openCatalogRoute('/cars')
      await screen.findByText('No matching cars.')
      expect(history.location.search).toBe('')
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
      fireEvent.change(screen.getByRole('textbox', { name: 'Search cars' }), {
        target: { value: 'Argo' },
      })
      await waitFor(() =>
        expect(new URLSearchParams(history.location.search).get('page')).toBe('0'),
      )
      await screen.findByRole('link', { name: 'Argo' })
      expect(api.listCars).toHaveBeenLastCalledWith(
        expect.objectContaining({
          q: 'Argo',
          name: '',
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

scenario(
  'Given no catalog pages, when the search completes, then pagination is hidden',
  async () => {
    vi.mocked(api.listCars).mockResolvedValue({
      content: [],
      number: 0,
      size: 12,
      totalElements: 0,
      totalPages: 0,
    })
    openCatalogRoute('/cars')
    await screen.findByText('No matching cars.')
    expect(screen.queryByRole('navigation', { name: 'Car pages' })).not.toBeInTheDocument()
  },
)

scenario(
  'Given the last of three pages, when going back, then keep filters, show the previous page number and enable forward navigation',
  async () => {
    vi.mocked(api.listCars).mockImplementation(async (search) => ({
      content: [{ ...car, name: `Car ${search.page}` }],
      number: search.page,
      size: 1,
      totalElements: 3,
      totalPages: 3,
    }))
    const history = openCatalogRoute('/cars?page=2&size=1&brand=Fiat')
    await screen.findByRole('link', { name: 'Car 2' })
    expect(screen.getByText('Page 3 of 3 (3 cars)')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Previous page' }))
    await screen.findByRole('link', { name: 'Car 1' })
    expect(screen.getByText('Page 2 of 3 (3 cars)')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Next page' })).toBeEnabled()
    const params = new URLSearchParams(history.location.search)
    expect(params.get('page')).toBe('1')
    expect(params.get('brand')).toBe('Fiat')
  },
)

scenario(
  'Given a legacy name URL, when editing the unified search, then remove the hidden name filter and reset the page',
  async () => {
    vi.mocked(api.listCars).mockResolvedValue({
      content: [],
      number: 0,
      size: 12,
      totalElements: 0,
      totalPages: 0,
    })
    const history = openCatalogRoute('/cars?name=Argo&page=2')
    const search = await screen.findByRole('textbox', { name: 'Search cars' })
    expect(search).toHaveValue('Argo')
    fireEvent.change(search, { target: { value: 'Fiat' } })
    await waitFor(() => expect(new URLSearchParams(history.location.search).get('q')).toBe('Fiat'))
    expect(new URLSearchParams(history.location.search).get('page')).toBe('0')
    expect(new URLSearchParams(history.location.search).get('name')).toBeNull()
    expect(search).toHaveValue('Fiat')
    fireEvent.change(search, { target: { value: '' } })
    await waitFor(() => expect(search).toHaveValue(''))
    expect(api.listCars).toHaveBeenLastCalledWith(
      expect.objectContaining({ q: '', name: '', page: 0 }),
      true,
      expect.any(AbortSignal),
    )
  },
)

scenario(
  'Given both query and legacy name in a URL, when opening the catalog, then show the query and preserve server refinements until editing',
  async () => {
    vi.mocked(api.listCars).mockResolvedValue({
      content: [],
      number: 0,
      size: 12,
      totalElements: 0,
      totalPages: 0,
    })
    openCatalogRoute('/cars?q=Fiat&name=Argo')
    expect(await screen.findByRole('textbox', { name: 'Search cars' })).toHaveValue('Fiat')
    expect(api.listCars).toHaveBeenCalledWith(
      expect.objectContaining({ q: 'Fiat', name: 'Argo' }),
      true,
      expect.any(AbortSignal),
    )
  },
)

scenario(
  'Given a numeric model in a shared URL, when opening the catalog, then the search stays numeric text instead of showing the whole fleet',
  async () => {
    vi.mocked(api.listCars).mockResolvedValue({
      content: [],
      number: 0,
      size: 12,
      totalElements: 0,
      totalPages: 0,
    })
    openCatalogRoute('/cars?q=208')
    expect(await screen.findByRole('textbox', { name: 'Search cars' })).toHaveValue('208')
    expect(api.listCars).toHaveBeenCalledWith(
      expect.objectContaining({ q: '208' }),
      true,
      expect.any(AbortSignal),
    )
  },
)

scenario(
  'Given a legacy name refinement alongside a global query, when removing that visible refinement, then preserve the query and other filters and reset pagination',
  async () => {
    vi.mocked(api.listCars).mockResolvedValue({
      content: [],
      number: 0,
      size: 12,
      totalElements: 0,
      totalPages: 0,
    })
    const history = openCatalogRoute('/cars?q=Fiat&name=Argo&brand=Fiat&category=HATCH&page=2')
    fireEvent.click(await screen.findByRole('button', { name: 'Clear name filter: Argo' }))
    await waitFor(() => expect(new URLSearchParams(history.location.search).get('name')).toBeNull())
    expect(screen.queryByRole('button', { name: /Clear name filter/ })).not.toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Search cars' })).toHaveValue('Fiat')
    expect(api.listCars).toHaveBeenLastCalledWith(
      expect.objectContaining({ q: 'Fiat', name: '', brand: 'Fiat', category: 'HATCH', page: 0 }),
      true,
      expect.any(AbortSignal),
    )
  },
)
