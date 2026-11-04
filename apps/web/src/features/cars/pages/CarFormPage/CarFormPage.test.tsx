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

describe('CarFormPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.getCar).mockResolvedValue(car)
    vi.mocked(listCategories).mockResolvedValue(categories)
    vi.mocked(listSpecifications).mockResolvedValue(specifications)
  })
  scenario(
    'Given a pending car edit, when the user leaves before it commits, then refresh the cache without reopening the saved car',
    async () => {
      let done!: (v: api.Car) => void
      vi.mocked(api.updateCar).mockImplementationOnce(
        () =>
          new Promise((r) => {
            done = r
          }),
      )
      const client = new QueryClient()
      client.setQueryData(['cars'], [car])
      const history = openCatalogRoute('/cars/second/edit', client)
      fireEvent.change(await screen.findByLabelText('Name'), { target: { value: 'Saved' } })
      fireEvent.click(screen.getByRole('button', { name: 'Save car' }))
      await waitFor(() => expect(api.updateCar).toHaveBeenCalled())
      fireEvent.click(screen.getByRole('link', { name: 'Rent Cars home' }))
      await waitFor(() => expect(history.location.pathname).toBe('/'))
      await act(async () => done(car))
      expect(history.location.pathname).toBe('/')
      expect(client.getQueryState(['cars'])?.isInvalidated).toBe(true)
    },
  )
  afterEach(() => vi.unstubAllGlobals())

  scenario(
    'Given a new car with catalogs loading, when the form is completed and saved, then the created record opens',
    async () => {
      let resolve!: (items: typeof categories) => void
      vi.mocked(listCategories).mockImplementationOnce(
        () =>
          new Promise((success) => {
            resolve = success
          }),
      )
      vi.mocked(api.createCar).mockResolvedValue(car)
      const history = openCatalogRoute('/cars/new')
      await screen.findByText('Loading vehicle catalogs…')
      expect(screen.getByRole('heading', { name: 'New car', level: 2 })).toBeVisible()
      await act(async () => resolve(categories))
      await screen.findByText('Save car')
      expect(screen.queryByText('Loading vehicle catalogs…')).not.toBeInTheDocument()
      for (const [label, value] of [
        ['Name', car.name],
        ['Description', car.description],
        ['Brand', car.brand],
        ['License plate', car.licensePlate],
        ['Daily rate (BRL)', '150,50'],
        ['Fine amount (BRL)', '5'],
        ['Category', car.categoryId],
      ])
        fireEvent.change(screen.getByLabelText(label), { target: { value } })
      fireEvent.click(screen.getByLabelText('GPS'))
      fireEvent.click(screen.getByLabelText('Available'))
      fireEvent.click(screen.getByText('Save car'))
      await screen.findByRole('heading', { name: 'Corolla' })
      expect(history.location.pathname).toBe('/cars/second')
      const {
        id: _id,
        version: _version,
        category: _category,
        specifications: _specifications,
        ...expected
      } = car
      expect(api.createCar).toHaveBeenCalledExactlyOnceWith(expected)
    },
  )

  scenario.each(['car', 'categories', 'specifications'] as const)(
    'Given an unsaved name, when the cached %s refresh fails, then the editor retains the draft through retry and patches only the name',
    async (resource) => {
      let stored = car
      const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
      vi.mocked(api.getCar).mockImplementation(async () => stored)
      vi.mocked(api.updateCar).mockImplementation(async (_id, patch) => {
        stored = { ...stored, ...patch }
        return stored
      })
      openCatalogRoute('/cars/second/edit', client)
      const name = await screen.findByLabelText('Name')
      fireEvent.change(name, { target: { value: 'My draft' } })
      stored = { ...stored, dailyRateCents: 42000 }
      const query = {
        car: api.getCar,
        categories: listCategories,
        specifications: listSpecifications,
      }[resource]
      vi.mocked(query).mockRejectedValueOnce(new Error('Car refresh failed'))
      await act(async () => {
        await client.invalidateQueries({
          queryKey: resource === 'car' ? ['car', car.id] : [resource],
        })
      })
      expect(await screen.findByRole('alert')).toHaveTextContent(
        resource === 'car' ? 'Car refresh failed' : 'Could not load vehicle catalogs.',
      )
      expect(screen.getByLabelText('Name')).toBe(name)
      expect(name).toHaveValue('My draft')
      expect(screen.getByRole('button', { name: 'Save car' })).toBeEnabled()
      fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
      await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
      expect(query).toHaveBeenCalledTimes(3)
      expect(screen.getByLabelText('Name')).toBe(name)
      fireEvent.click(screen.getByRole('button', { name: 'Save car' }))
      expect(await screen.findByRole('heading', { name: 'My draft' })).toBeVisible()
      expect(api.updateCar).toHaveBeenCalledExactlyOnceWith(car.id, {
        name: 'My draft',
        expectedVersion: car.version,
      })
      expect(screen.getByText('R$ 420,00')).toBeVisible()
    },
  )

  scenario.each([
    { cache: 'unchanged', refetch: false },
    { cache: 'refreshed', refetch: true },
  ])(
    'Given an open editor with a $cache cache, when another writer changes a different field, then the original version is rejected and the draft survives',
    async ({ refetch }) => {
      let stored = { ...car, version: 4 }
      const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
      vi.mocked(api.getCar).mockImplementation(async () => stored)
      const real = await vi.importActual<typeof api>('../../cars-api')
      vi.mocked(api.updateCar).mockImplementation(real.updateCar)
      openCatalogRoute('/cars/second/edit', client)
      fireEvent.change(await screen.findByLabelText('Name'), { target: { value: 'Renamed' } })
      stored = { ...stored, version: 5, dailyRateCents: 22000 }
      if (refetch)
        await act(async () => {
          await client.invalidateQueries({ queryKey: ['car', car.id] })
        })
      const unrelated = ['car', car.id, 'history']
      client.setQueryData(unrelated, ['earlier'])
      vi.mocked(fetch).mockResolvedValueOnce(
        new Response(null, {
          status: 409,
          headers: { 'Content-Type': 'application/problem+json' },
        }),
      )
      fireEvent.click(screen.getByRole('button', { name: 'Save car' }))
      expect(await screen.findByRole('alert')).toHaveTextContent(
        'The record changed. Reopen the editor',
      )
      expect(screen.getByLabelText('Name')).toHaveValue('Renamed')
      expect(api.updateCar).toHaveBeenCalledExactlyOnceWith(car.id, {
        name: 'Renamed',
        expectedVersion: 4,
      })
      await waitFor(() => expect(client.getQueryData(['car', car.id])).toEqual(stored))
      expect(client.getQueryState(unrelated)?.isInvalidated).toBe(false)
    },
  )
  scenario(
    'Given an edit draft, when the server rejects it and the user retries, then the draft survives and the updated record appears',
    async () => {
      const updated = { ...car, name: 'Renamed' }
      const http = await vi.importActual<typeof api>('../../cars-api')
      vi.mocked(api.updateCar).mockImplementationOnce(http.updateCar).mockResolvedValue(updated)
      vi.mocked(api.getCar).mockResolvedValueOnce(car).mockResolvedValue(updated)
      const history = openCatalogRoute('/cars/second/edit')
      vi.mocked(fetch).mockResolvedValueOnce(
        Response.json(
          { errors: { dailyRateCents: ['must be greater than or equal to 0'] } },
          { status: 422 },
        ),
      )
      fireEvent.change(await screen.findByLabelText('Name'), { target: { value: 'Renamed' } })
      expect(screen.getByRole('heading', { name: 'Edit car', level: 2 })).toBeVisible()
      fireEvent.click(screen.getByText('Save car'))
      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Daily rate (BRL): must be greater than or equal to 0',
      )
      expect(api.getCar).toHaveBeenCalledTimes(1)
      expect(screen.getByLabelText('Name')).toHaveValue('Renamed')
      fireEvent.click(screen.getByText('Save car'))
      expect(await screen.findByRole('heading', { name: 'Renamed' })).toBeVisible()
      expect(api.updateCar).toHaveBeenLastCalledWith(car.id, {
        name: 'Renamed',
        expectedVersion: car.version,
      })
      expect(history.location.pathname).toBe('/cars/second')
    },
  )
})
