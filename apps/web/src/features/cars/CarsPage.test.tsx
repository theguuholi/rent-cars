import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, test as scenario, vi } from 'vitest'
import { openCatalogRoute } from '../../test/open-catalog-route'
import { car, categories, specifications } from '../../test/car-fixtures'
import * as api from './cars-api'
import { listCategories } from '../categories/categories-api'
import { listSpecifications } from '../specifications/specifications-api'

vi.mock('./cars-api')
vi.mock('../categories/categories-api')
vi.mock('../specifications/specifications-api')

describe('Car catalog and record pages', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listCars).mockResolvedValue([])
    vi.mocked(api.getCar).mockResolvedValue(car)
    vi.mocked(listCategories).mockResolvedValue(categories)
    vi.mocked(listSpecifications).mockResolvedValue(specifications)
  })
  afterEach(() => vi.unstubAllGlobals())

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
      expect(await screen.findByText('No cars yet.')).toBeVisible()
      expect(screen.getByRole('link', { name: 'New car' })).toHaveAttribute('href', '/cars/new')
    },
  )

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
        .mockResolvedValue({ ...car, available: true, specificationIds: [] })
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
    },
  )

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
      const { id: _id, ...expected } = car
      expect(api.createCar).toHaveBeenCalledExactlyOnceWith(expected)
    },
  )

  scenario(
    'Given an edit draft, when the server rejects it and the user retries, then the draft survives and the updated record appears',
    async () => {
      const updated = { ...car, name: 'Renamed' }
      vi.mocked(api.updateCar)
        .mockRejectedValueOnce(new Error('Save refused'))
        .mockResolvedValue(updated)
      vi.mocked(api.getCar).mockResolvedValueOnce(car).mockResolvedValue(updated)
      const history = openCatalogRoute('/cars/second/edit')
      fireEvent.change(await screen.findByLabelText('Name'), { target: { value: 'Renamed' } })
      expect(screen.getByRole('heading', { name: 'Edit car', level: 2 })).toBeVisible()
      fireEvent.click(screen.getByText('Save car'))
      expect(await screen.findByRole('alert')).toHaveTextContent('Save refused')
      expect(screen.getByLabelText('Name')).toHaveValue('Renamed')
      fireEvent.click(screen.getByText('Save car'))
      expect(await screen.findByRole('heading', { name: 'Renamed' })).toBeVisible()
      const { id, licensePlate: _plate, ...expected } = updated
      expect(api.updateCar).toHaveBeenLastCalledWith(id, expected)
      expect(history.location.pathname).toBe('/cars/second')
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
      expect(await screen.findByText('No cars yet.')).toBeVisible()
      expect(history.location.pathname).toBe('/cars')
      expect(api.deleteCar).toHaveBeenCalledTimes(2)
      expect(api.deleteCar).toHaveBeenLastCalledWith('second')
    },
  )
})
