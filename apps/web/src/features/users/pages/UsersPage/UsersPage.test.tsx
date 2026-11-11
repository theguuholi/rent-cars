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

describe('UsersPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
    vi.unstubAllGlobals()
  })

  test('Given users, when opening the list, then show accounts and a link to the selected record', async () => {
    vi.mocked(api.listUsers).mockResolvedValue([user])
    vi.mocked(api.getUser).mockResolvedValue(user)
    openCatalogRoute('/users')
    expect(await screen.findByRole('row', { name: /driver@example.test/ })).toBeVisible()
    fireEvent.click(screen.getByRole('link', { name: 'View driver@example.test' }))
    expect(await screen.findByRole('heading', { name: 'Test Driver' })).toBeVisible()
  })

  test('Given no users, when opening the list, then show the empty state and creation action', async () => {
    vi.mocked(api.listUsers).mockResolvedValue([])
    openCatalogRoute('/users')
    expect(await screen.findByText('No users found.')).toBeVisible()
    expect(screen.getByRole('link', { name: 'New user' })).toHaveAttribute('href', '/users/new')
  })

  test('Given a failed list, when retrying, then show recovered accounts', async () => {
    vi.mocked(api.listUsers).mockRejectedValueOnce(new Error('Offline')).mockResolvedValue([user])
    openCatalogRoute('/users')
    expect(await screen.findByRole('alert')).toHaveTextContent('Offline')
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('driver@example.test')).toBeVisible()
  })

  test('Given a driver, when opening Users directly, then deny the page', async () => {
    const history = openCatalogRoute('/users', undefined, {
      accessToken: 'driver-token',
      user: { id: 'driver', email: 'driver@example.test', role: 'USER' },
    })
    expect(await screen.findByRole('heading', { name: 'Access denied' })).toBeVisible()
    expect(history.location.pathname).toBe('/forbidden')
    expect(api.listUsers).not.toHaveBeenCalled()
  })
})
