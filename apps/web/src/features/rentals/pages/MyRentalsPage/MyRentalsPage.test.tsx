import { QueryClient } from '@tanstack/react-query'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { openCatalogRoute } from '../../../../test/open-catalog-route'
import { listRentals, type Rental } from '../../rentals-api'

vi.mock('../../rentals-api')
const driver = {
  accessToken: 'driver',
  user: { id: 'driver', email: 'driver@example.test', role: 'USER' as const },
}
const rental: Rental = {
  id: 'rental-1',
  carId: 'car-1',
  car: { name: 'Argo' },
  startDate: '2026-11-06T15:00:00Z',
  expectedReturnDate: '2026-11-07T15:00:00Z',
  endDate: '2026-11-09T15:00:00Z',
  totalCents: 36000,
  dailyRateCents: 10000,
  fineAmountCents: 3000,
  billedDays: 3,
  lateDays: 2,
}

describe('MyRentalsPage', () => {
  beforeEach(() => vi.resetAllMocks())
  afterEach(() => vi.unstubAllGlobals())

  test('Given a returned rental, when the driver opens My rentals, then its receipt shows the agreed costs and final total', async () => {
    vi.mocked(listRentals).mockResolvedValue([rental])
    const history = openCatalogRoute('/rentals', undefined, driver)
    expect(await screen.findByRole('link', { name: 'My rentals' })).toHaveAttribute(
      'href',
      '/rentals',
    )
    const receipt = await screen.findByRole('article', { name: 'Argo rental' })
    expect(within(receipt).getByText('Returned', { exact: true })).toBeVisible()
    expect(screen.queryByText('No rentals yet.')).not.toBeInTheDocument()
    expect(within(receipt).getByText('Returned at').nextElementSibling).toHaveTextContent(
      new Date(rental.endDate!).toLocaleString(),
    )
    expect(within(receipt).getByText('Agreed daily rate').nextElementSibling).toHaveTextContent(
      '100,00',
    )
    expect(receipt).toHaveTextContent('3 completed day(s)')
    expect(receipt).toHaveTextContent('2 completed day(s)')
    expect(receipt).toHaveTextContent('Total charge')
    expect(receipt).toHaveTextContent('360,00')
    expect(screen.getByRole('link', { name: 'Argo' })).toHaveAttribute('href', '/cars/car-1')
    expect(history.location.pathname).toBe('/rentals')
    await waitFor(() => expect(document.title).toBe('My rentals | Rent Cars'))
  })

  test('Given an open rental, when the driver opens My rentals, then no final receipt appears', async () => {
    vi.mocked(listRentals).mockResolvedValue([
      { ...rental, endDate: null, totalCents: null, billedDays: null, lateDays: null },
    ])
    openCatalogRoute('/rentals', undefined, driver)
    expect(await screen.findByText('Final charge is calculated on return.')).toBeVisible()
    expect(screen.getByText('Open')).toBeVisible()
    expect(screen.queryByText(/Total charge/)).not.toBeInTheDocument()
    expect(screen.queryByText('Returned at')).not.toBeInTheDocument()
  })

  test('Given a legacy rental, when viewing its receipt, then the recorded total remains available without an invented breakdown', async () => {
    vi.mocked(listRentals).mockResolvedValue([
      { ...rental, dailyRateCents: null, fineAmountCents: null, billedDays: null, lateDays: null },
    ])
    openCatalogRoute('/rentals', undefined, driver)
    expect(await screen.findByText('Historical charge breakdown is unavailable.')).toBeVisible()
    expect(screen.getByText(/Total charge/)).toHaveTextContent('360,00')
  })

  test('Given a pending rental lookup, when opening My rentals, then loading is visible', async () => {
    vi.mocked(listRentals).mockReturnValue(new Promise(() => {}))
    openCatalogRoute('/rentals', undefined, driver)
    expect(await screen.findByRole('status')).toHaveTextContent('Loading your rentals…')
  })

  test('Given no rentals, when the driver opens My rentals, then the empty state appears', async () => {
    vi.mocked(listRentals).mockResolvedValue([])
    openCatalogRoute('/rentals', undefined, driver)
    expect(await screen.findByText('No rentals yet.')).toBeVisible()
  })

  test('Given a failed lookup, when the driver retries, then the rentals load', async () => {
    vi.mocked(listRentals)
      .mockRejectedValueOnce(new Error('Unavailable'))
      .mockResolvedValueOnce([rental])
    openCatalogRoute('/rentals', undefined, driver)
    expect(await screen.findByRole('alert')).toHaveTextContent('Unavailable Try again')
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('article', { name: 'Argo rental' })).toBeVisible()
  })
})

describe('MyRentalsPage incomplete receipts and cache', () => {
  test.each(['dailyRateCents', 'fineAmountCents', 'billedDays', 'lateDays'] as const)(
    'Given a returned rental missing %s, when opening its receipt, then hide the breakdown instead of inventing a value',
    async (field) => {
      vi.mocked(listRentals).mockResolvedValue([{ ...rental, [field]: null }])
      openCatalogRoute('/rentals', undefined, driver)
      expect(await screen.findByText('Historical charge breakdown is unavailable.')).toBeVisible()
      expect(screen.queryByText('Daily charge')).not.toBeInTheDocument()
      expect(screen.queryByText('Late fee')).not.toBeInTheDocument()
      if (field === 'dailyRateCents')
        expect(screen.queryByText('Agreed daily rate')).not.toBeInTheDocument()
    },
  )

  test('Given a returned rental without a recorded total, when displaying its receipt, then do not invent a total charge', async () => {
    vi.mocked(listRentals).mockResolvedValue([{ ...rental, totalCents: null }])
    openCatalogRoute('/rentals', undefined, driver)
    await screen.findByRole('region', { name: 'Receipt' })
    expect(screen.queryByText(/Total charge/)).not.toBeInTheDocument()
  })

  test('Given fresh cached rentals, when opening My rentals, then render the existing receipt without another request', async () => {
    vi.mocked(listRentals).mockClear()
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false, staleTime: Infinity } },
    })
    client.setQueryData(['rentals'], [rental])
    openCatalogRoute('/rentals', client, driver)
    expect(await screen.findByRole('article', { name: 'Argo rental' })).toBeVisible()
    expect(listRentals).not.toHaveBeenCalled()
  })
})
