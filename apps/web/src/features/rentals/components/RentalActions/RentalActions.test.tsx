import { QueryClient } from '@tanstack/react-query'
import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, test as scenario, vi } from 'vitest'
import { car } from '../../../../test/car-fixtures'
import { openCatalogRoute } from '../../../../test/open-catalog-route'
import { getCar } from '../../../cars/cars-api'
import { listRentals, rentCar, returnRental, type Rental } from '../../rentals-api'

vi.mock('../../../cars/cars-api', async (original) => ({
  ...(await original<typeof import('../../../cars/cars-api')>()),
  getCar: vi.fn(),
}))
vi.mock('../../rentals-api')

const driver = {
  accessToken: 'driver-token',
  user: { id: 'driver', email: 'driver@example.test', role: 'USER' as const },
}
const openRental: Rental = {
  id: 'rental',
  carId: car.id,
  startDate: '2026-11-09T12:00:00Z',
  expectedReturnDate: '2026-11-12T12:00:00Z',
  endDate: null,
  totalCents: null,
}
const client = () => new QueryClient({ defaultOptions: { queries: { retry: false } } })

async function openCar(available = true, rentals: Rental[] = []) {
  vi.mocked(getCar).mockResolvedValue({ ...car, available })
  vi.mocked(listRentals).mockResolvedValue(rentals)
  const query = client()
  openCatalogRoute('/cars/second', query, driver)
  await screen.findByRole('heading', { name: 'Corolla' })
  return query
}

function chooseReturn(value: string) {
  fireEvent.change(screen.getByLabelText('Expected return'), { target: { value } })
}

describe('RentalActions', () => {
  beforeEach(() => vi.resetAllMocks())
  afterEach(() => vi.unstubAllGlobals())

  scenario(
    'Given a visitor or admin, when viewing a car, then rental controls stay hidden',
    async () => {
      vi.mocked(getCar).mockResolvedValue(car)
      openCatalogRoute('/cars/second', undefined, null)
      await screen.findByRole('heading', { name: 'Corolla' })
      expect(screen.queryByRole('heading', { name: 'Rental' })).not.toBeInTheDocument()
      expect(listRentals).not.toHaveBeenCalled()
    },
  )

  scenario(
    'Given a driver, when rentals load, then only an available car can be rented',
    async () => {
      await openCar(false)
      expect(await screen.findByText('This car is unavailable to rent.')).toBeVisible()
      expect(screen.queryByRole('button', { name: 'Rent car' })).not.toBeInTheDocument()
      expect(listRentals).toHaveBeenCalledWith(expect.any(AbortSignal))
    },
  )

  scenario('Given a pending rental lookup, when opening a car, then loading is shown', async () => {
    vi.mocked(getCar).mockResolvedValue({ ...car, available: false })
    vi.mocked(listRentals).mockReturnValue(new Promise(() => {}))
    openCatalogRoute('/cars/second', client(), driver)
    expect(await screen.findByText('Loading your rentals…')).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Rent car' })).not.toBeInTheDocument()
    expect(screen.queryByText('This car is unavailable to rent.')).not.toBeInTheDocument()
  })

  scenario(
    'Given rentals for other or returned cars, when viewing this car, then it remains rentable',
    async () => {
      await openCar(true, [
        { ...openRental, carId: 'another-car' },
        { ...openRental, id: 'returned', endDate: '2026-11-10T12:00:00Z' },
      ])
      expect(await screen.findByRole('button', { name: 'Rent car' })).toBeVisible()
      expect(screen.queryByRole('button', { name: 'Return car' })).not.toBeInTheDocument()
    },
  )

  scenario(
    'Given an open rental for this car, when viewing it, then its return date is shown and the rent form is hidden',
    async () => {
      await openCar(false, [openRental])
      expect(await screen.findByRole('button', { name: 'Return car' })).toBeVisible()
      expect(screen.getByText(/Your rental is open/).textContent).toBe(
        `Your rental is open. Expected return: ${new Date(openRental.expectedReturnDate).toLocaleString()}.`,
      )
      expect(screen.queryByRole('button', { name: 'Rent car' })).not.toBeInTheDocument()
      expect(screen.queryByText('This car is unavailable to rent.')).not.toBeInTheDocument()
    },
  )

  scenario('Given a failed rental lookup, when retrying, then the form appears', async () => {
    vi.mocked(getCar).mockResolvedValue({ ...car, available: true })
    vi.mocked(listRentals)
      .mockRejectedValueOnce(new Error('Lookup failed'))
      .mockResolvedValueOnce([])
    openCatalogRoute('/cars/second', client(), driver)
    expect((await screen.findByRole('alert')).textContent).toBe('Lookup failed Try again')
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('button', { name: 'Rent car' })).toBeVisible()
  })

  scenario('Given an invalid return time, when submitting, then no rental is opened', async () => {
    await openCar()
    await screen.findByRole('button', { name: 'Rent car' })
    expect(screen.getByLabelText('Expected return')).toHaveValue('')
    fireEvent.click(screen.getByRole('button', { name: 'Rent car' }))
    expect(await screen.findByText('Choose a return date and time.')).toBeVisible()
    chooseReturn('2020-01-01T12:00')
    expect(
      await screen.findByText('Choose a return time at least 24 hours from now.'),
    ).toBeVisible()
    expect(screen.getByLabelText('Expected return')).toHaveAttribute('aria-invalid', 'true')
    expect(rentCar).not.toHaveBeenCalled()
  })

  scenario(
    'Given a nonempty year outside the JavaScript date range, when choosing a return time, then the form rejects it',
    async () => {
      await openCar()
      await screen.findByRole('button', { name: 'Rent car' })
      chooseReturn('10000-01-01T00:00')
      expect(await screen.findByText('Choose a return date and time.')).toBeVisible()
      fireEvent.click(screen.getByRole('button', { name: 'Rent car' }))
      expect(rentCar).not.toHaveBeenCalled()
    },
  )

  scenario(
    'Given a return time near the 24-hour boundary, when correcting it, then only the valid time can be submitted',
    async () => {
      await openCar()
      await screen.findByRole('button', { name: 'Rent car' })
      const local = (offsetHours: number) => {
        const date = new Date(Date.now() + offsetHours * 60 * 60 * 1000)
        const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60 * 1000)
        return localDate.toISOString().slice(0, 16)
      }
      chooseReturn(local(23))
      expect(
        await screen.findByText('Choose a return time at least 24 hours from now.'),
      ).toBeVisible()
      chooseReturn(local(25))
      await waitFor(() =>
        expect(
          screen.queryByText('Choose a return time at least 24 hours from now.'),
        ).not.toBeInTheDocument(),
      )
      expect(screen.getByLabelText('Expected return')).toHaveAttribute('aria-invalid', 'false')
      fireEvent.click(screen.getByRole('button', { name: 'Rent car' }))
      await waitFor(() => expect(rentCar).toHaveBeenCalledTimes(1))
    },
  )

  scenario(
    'Given a valid return time exactly 24 hours ahead, when submitting, then the rental is accepted',
    async () => {
      await openCar()
      await screen.findByRole('button', { name: 'Rent car' })
      vi.useFakeTimers({ shouldAdvanceTime: true })
      try {
        const now = new Date('2026-11-09T12:00:00.000Z')
        vi.setSystemTime(now)
        const date = new Date(now.getTime() + 24 * 60 * 60 * 1000)
        const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60 * 1000)
        chooseReturn(localDate.toISOString().slice(0, 16))
        fireEvent.click(screen.getByRole('button', { name: 'Rent car' }))
        await waitFor(() => expect(rentCar).toHaveBeenCalledTimes(1))
      } finally {
        vi.useRealTimers()
      }
    },
  )

  scenario('Given a form submit, when renting, then browser navigation is prevented', async () => {
    await openCar()
    await screen.findByRole('button', { name: 'Rent car' })
    const event = new Event('submit', { bubbles: true, cancelable: true })
    screen.getByRole('form', { name: 'Rent this car' }).dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)
  })

  scenario(
    'Given a competing rental, when opening this car returns 409, then the driver sees the conflict and can retry',
    async () => {
      await openCar()
      await screen.findByRole('button', { name: 'Rent car' })
      vi.mocked(rentCar)
        .mockRejectedValueOnce(
          new Error('This car has already been rented. Refresh the car details.'),
        )
        .mockResolvedValueOnce(openRental)
      chooseReturn('2099-11-12T12:00')
      fireEvent.click(screen.getByRole('button', { name: 'Rent car' }))
      expect(
        await screen.findByText('This car has already been rented. Refresh the car details.'),
      ).toBeVisible()
      expect(screen.getByLabelText('Expected return')).toHaveValue('2099-11-12T12:00')
      chooseReturn('2099-11-13T12:00')
      await waitFor(() =>
        expect(
          screen.queryByText('This car has already been rented. Refresh the car details.'),
        ).not.toBeInTheDocument(),
      )
      fireEvent.click(screen.getByRole('button', { name: 'Rent car' }))
      await waitFor(() => expect(rentCar).toHaveBeenCalledTimes(2))
      expect(rentCar).toHaveBeenLastCalledWith(car.id, new Date('2099-11-13T12:00').toISOString())
    },
  )

  scenario(
    'Given a successful rental, when the cache refreshes, then return replaces the rent form',
    async () => {
      const query = await openCar()
      const invalidate = vi.spyOn(query, 'invalidateQueries')
      vi.mocked(rentCar).mockResolvedValue(openRental)
      vi.mocked(listRentals).mockResolvedValueOnce([openRental])
      await screen.findByRole('button', { name: 'Rent car' })
      chooseReturn('2099-11-12T12:00')
      fireEvent.click(screen.getByRole('button', { name: 'Rent car' }))
      expect(await screen.findByRole('button', { name: 'Return car' })).toBeVisible()
      expect(screen.queryByRole('button', { name: 'Rent car' })).not.toBeInTheDocument()
      expect(query.getQueryData<Rental[]>(['rentals'])).toEqual([openRental])
      expect(invalidate).toHaveBeenCalledWith({ queryKey: ['rentals'] })
      expect(invalidate).toHaveBeenCalledWith({ queryKey: ['car', car.id], exact: true })
      expect(invalidate).toHaveBeenCalledWith({ queryKey: ['cars'] })
    },
  )

  scenario(
    'Given a successful rental with stale list data, when the form remains visible, then its date is cleared',
    async () => {
      await openCar()
      vi.mocked(rentCar).mockResolvedValue(openRental)
      await screen.findByRole('button', { name: 'Rent car' })
      chooseReturn('2099-11-12T12:00')
      fireEvent.click(screen.getByRole('button', { name: 'Rent car' }))
      expect(await screen.findByText('Car rented.')).toBeVisible()
      expect(screen.getByLabelText('Expected return')).toHaveValue('')
    },
  )

  scenario(
    'Given a rent error, when returning a rental found on refresh, then the old error clears',
    async () => {
      const query = await openCar()
      vi.mocked(rentCar).mockRejectedValue(new Error('Rent failed'))
      vi.mocked(returnRental).mockReturnValue(new Promise(() => {}))
      await screen.findByRole('button', { name: 'Rent car' })
      chooseReturn('2099-11-12T12:00')
      fireEvent.click(screen.getByRole('button', { name: 'Rent car' }))
      expect(await screen.findByText('Rent failed')).toBeVisible()
      await act(async () => query.setQueryData(['rentals'], [openRental]))
      fireEvent.click(await screen.findByRole('button', { name: 'Return car' }))
      await waitFor(() => expect(screen.queryByText('Rent failed')).not.toBeInTheDocument())
      expect(returnRental).toHaveBeenCalledExactlyOnceWith(openRental.id)
    },
  )

  scenario(
    'Given a return error, when choosing a date for a newly available car, then the old error clears',
    async () => {
      const query = await openCar(true, [openRental])
      vi.mocked(returnRental).mockRejectedValue(new Error('Return failed'))
      fireEvent.click(await screen.findByRole('button', { name: 'Return car' }))
      expect(await screen.findByText('Return failed')).toBeVisible()
      await act(async () => query.setQueryData(['rentals'], []))
      await screen.findByRole('button', { name: 'Rent car' })
      chooseReturn('2099-11-12T12:00')
      await waitFor(() => expect(screen.queryByText('Return failed')).not.toBeInTheDocument())
    },
  )

  scenario(
    'Given pending rental writes, when buttons are pressed again, then each action is submitted once',
    async () => {
      await openCar()
      let finishRent!: (value: Rental) => void
      vi.mocked(rentCar).mockReturnValue(
        new Promise((resolve) => {
          finishRent = resolve
        }),
      )
      vi.mocked(listRentals).mockResolvedValueOnce([openRental])
      await screen.findByRole('button', { name: 'Rent car' })
      chooseReturn('2099-11-12T12:00')
      fireEvent.click(screen.getByRole('button', { name: 'Rent car' }))
      expect(await screen.findByRole('button', { name: 'Renting…' })).toBeDisabled()
      expect(rentCar).toHaveBeenCalledTimes(1)
      await act(async () => finishRent(openRental))
      const returnButton = await screen.findByRole('button', { name: 'Return car' })
      let finishReturn!: (value: Rental) => void
      vi.mocked(returnRental).mockReturnValue(
        new Promise((resolve) => {
          finishReturn = resolve
        }),
      )
      const returned = { ...openRental, endDate: '2026-11-10T12:00:00Z', totalCents: 15050 }
      vi.mocked(listRentals).mockResolvedValueOnce([returned])
      fireEvent.click(returnButton)
      expect(await screen.findByRole('button', { name: 'Returning…' })).toBeDisabled()
      expect(returnRental).toHaveBeenCalledExactlyOnceWith(openRental.id)
      await act(async () => finishReturn(returned))
      expect(await screen.findByText('Car returned.')).toBeVisible()
      expect(await screen.findByRole('button', { name: 'Rent car' })).toBeVisible()
      vi.mocked(rentCar).mockResolvedValueOnce({ ...openRental, id: 'second-rental' })
      chooseReturn('2099-11-13T12:00')
      fireEvent.click(screen.getByRole('button', { name: 'Rent car' }))
      await waitFor(() => expect(rentCar).toHaveBeenCalledTimes(2))
    },
  )

  scenario(
    'Given an open rental, when return fails and is retried, then the successful return refreshes the car',
    async () => {
      const query = await openCar(false, [openRental])
      const returned = { ...openRental, endDate: '2026-11-10T12:00:00Z', totalCents: 15050 }
      vi.mocked(returnRental)
        .mockRejectedValueOnce(new Error('Rental has already been returned'))
        .mockResolvedValueOnce(returned)
      vi.mocked(listRentals).mockResolvedValueOnce([returned])
      vi.mocked(getCar).mockResolvedValue({ ...car, available: true })
      const button = await screen.findByRole('button', { name: 'Return car' })
      fireEvent.click(button)
      expect(await screen.findByText('Rental has already been returned')).toBeVisible()
      fireEvent.click(button)
      await waitFor(() => expect(returnRental).toHaveBeenCalledTimes(2))
      expect(await screen.findByText('Car returned.')).toBeVisible()
      expect(await screen.findByRole('button', { name: 'Rent car' })).toBeVisible()
      expect(query.getQueryData(['car', car.id])).toMatchObject({ available: true })
    },
  )
})
