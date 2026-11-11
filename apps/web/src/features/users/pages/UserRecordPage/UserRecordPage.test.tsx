import { fireEvent, screen } from '@testing-library/react'
import { afterEach, describe, expect, test, vi } from 'vitest'
import { openCatalogRoute } from '../../../../test/open-catalog-route'
import * as api from '../../users-api'

vi.mock('../../users-api')
const user = {
  id: 'user-1',
  email: 'driver@example.test',
  role: 'USER' as const,
  firstName: 'Test',
  lastName: 'Driver',
  userName: 'test.driver',
  driveLicense: '01234567890',
}

describe('UserRecordPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
    vi.unstubAllGlobals()
  })
  test('Given a driver, when opening the detail URL, then show private details and actions', async () => {
    vi.mocked(api.getUser).mockResolvedValue(user)
    openCatalogRoute('/users/user-1')
    expect(await screen.findByRole('heading', { name: 'Test Driver' })).toBeVisible()
    expect(screen.getByText('01234567890')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Edit user' })).toHaveAttribute(
      'href',
      '/users/user-1/edit',
    )
  })
  test('Given a missing user, when retrying, then show the recovered record', async () => {
    vi.mocked(api.getUser).mockRejectedValueOnce(new Error('Gone')).mockResolvedValue(user)
    openCatalogRoute('/users/user-1')
    expect(await screen.findByRole('alert')).toHaveTextContent('Gone')
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: 'Test Driver' })).toBeVisible()
  })
  test('Given a driver, when confirming deletion, then return to the list', async () => {
    vi.mocked(api.getUser).mockResolvedValue(user)
    vi.mocked(api.deleteUser).mockResolvedValue()
    vi.mocked(api.listUsers).mockResolvedValue([])
    const history = openCatalogRoute('/users/user-1/delete')
    fireEvent.click(await screen.findByRole('button', { name: 'Confirm deletion' }))
    expect(await screen.findByText('No users found.')).toBeVisible()
    expect(history.location.pathname).toBe('/users')
    expect(api.deleteUser).toHaveBeenCalledWith('user-1')
  })
  test('Given rental history, when deletion fails, then keep the confirmation visible', async () => {
    vi.mocked(api.getUser).mockResolvedValue(user)
    vi.mocked(api.deleteUser).mockRejectedValue(new Error('Rental history'))
    openCatalogRoute('/users/user-1/delete')
    fireEvent.click(await screen.findByRole('button', { name: 'Confirm deletion' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Rental history')
    expect(screen.getByRole('heading', { name: 'Delete driver@example.test?' })).toBeVisible()
  })
})
