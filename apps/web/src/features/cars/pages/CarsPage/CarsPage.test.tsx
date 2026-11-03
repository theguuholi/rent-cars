import { act, fireEvent, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, test as scenario, vi } from 'vitest'
import { openCatalogRoute } from '../../../../test/open-catalog-route'
import * as api from '../../cars-api'

vi.mock('../../cars-api')

describe('CarsPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listCars).mockResolvedValue([])
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
})
