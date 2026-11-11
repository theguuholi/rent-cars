import { afterEach, describe, expect, test, vi } from 'vitest'
import { session } from '../auth/session'
import { createUser, deleteUser, getUser, listUsers, updateUser } from './users-api'

const user = {
  id: 'user-1',
  email: 'driver@example.test',
  role: 'USER' as const,
  firstName: 'Test',
  lastName: 'Driver',
  userName: 'test.driver',
  driveLicense: '01234567890',
}
const input = {
  firstName: 'Test',
  lastName: 'Driver',
  userName: 'test.driver',
  driveLicense: '01234567890',
  email: user.email,
  password: 'test-password-123',
  passwordConfirmation: 'test-password-123',
}

describe('users-api', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    session.set(null)
  })

  test('Given an admin, when listing and reading, then send bearer requests and parse records', async () => {
    session.set({
      accessToken: 'admin-token',
      user: { id: 'admin', email: 'admin@example.test', role: 'ADMIN' },
    })
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(Response.json([user]))
      .mockResolvedValueOnce(Response.json(user))
    vi.stubGlobal('fetch', fetcher)
    const signal = new AbortController().signal
    expect(await listUsers(signal)).toEqual([user])
    expect(await getUser('user-1', signal)).toEqual(user)
    expect(fetcher).toHaveBeenNthCalledWith(
      1,
      '/api/admin/users',
      expect.objectContaining({ signal, headers: { Authorization: 'Bearer admin-token' } }),
    )
    expect(fetcher).toHaveBeenNthCalledWith(
      2,
      '/api/admin/users/user-1',
      expect.objectContaining({ signal }),
    )
  })

  test('Given valid details, when creating, editing and deleting, then use the expected methods and bodies', async () => {
    session.set({
      accessToken: 'admin-token',
      user: { id: 'admin', email: 'admin@example.test', role: 'ADMIN' },
    })
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(Response.json(user, { status: 201 }))
      .mockResolvedValueOnce(Response.json(user))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetcher)
    expect(await createUser(input, 'USER')).toEqual(user)
    expect(await updateUser('user-1', user)).toEqual(user)
    await deleteUser('user-1')
    expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({ user: input, role: 'USER' })
    expect(fetcher.mock.calls[0][1].method).toBe('POST')
    expect(fetcher.mock.calls[1][1].method).toBe('PUT')
    expect(fetcher.mock.calls[2][1].method).toBe('DELETE')
  })

  test.each([
    [404, 'no longer exists'],
    [409, 'already exist'],
    [422, 'Check the user'],
    [500, 'Could not manage'],
  ])('Given HTTP %i, when reading, then show a meaningful error', async (status, message) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status })))
    await expect(getUser('missing')).rejects.toThrow(message)
  })
})
