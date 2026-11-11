import { screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { openCatalogRoute } from '../../test/open-catalog-route'
import * as api from './users-api'

vi.mock('./users-api')

const user = {
  id: 'user-2',
  email: 'driver@example.test',
  role: 'USER' as const,
  firstName: 'Test',
  lastName: 'Driver',
  userName: 'test.driver',
  driveLicense: '01234567890',
}

describe('Users routes', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(api.listUsers).mockResolvedValue([user])
    vi.mocked(api.getUser).mockResolvedValue(user)
  })
  afterEach(() => vi.unstubAllGlobals())

  test.each([
    ['/users', 'Users', 'Users'],
    ['/users/new', 'New user', 'New user'],
    ['/users/user-2', 'User details', 'Test Driver'],
    ['/users/user-2/edit', 'Edit user', 'Edit user'],
    ['/users/user-2/delete', 'Delete user', 'Delete driver@example.test?'],
  ])('Given an admin, when opening %s directly, then show %s', async (path, title, heading) => {
    const history = openCatalogRoute(path)
    expect(await screen.findByRole('heading', { name: heading })).toBeVisible()
    expect(history.location.pathname).toBe(path)
    await waitFor(() => expect(document.title).toBe(`${title} | Rent Cars`))
    if (path.includes('user-2')) {
      expect(api.getUser).toHaveBeenCalledExactlyOnceWith('user-2', expect.any(AbortSignal))
      expect(api.listUsers).not.toHaveBeenCalled()
    }
  })

  test('Given a driver, when opening an admin URL, then redirect before requesting accounts', async () => {
    const history = openCatalogRoute('/users/user-2/edit', undefined, {
      accessToken: 'driver-token',
      user: { id: 'driver', email: 'driver@example.test', role: 'USER' },
    })
    await waitFor(() => expect(history.location.pathname).toBe('/forbidden'))
    expect(api.getUser).not.toHaveBeenCalled()
    expect(api.listUsers).not.toHaveBeenCalled()
  })

  test('Given a guest, when opening Users, then redirect to sign in', async () => {
    const history = openCatalogRoute('/users', undefined, null)
    await waitFor(() => expect(history.location.pathname).toBe('/login'))
    expect(api.listUsers).not.toHaveBeenCalled()
  })
})
