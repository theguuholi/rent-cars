import { fireEvent, screen, waitFor } from '@testing-library/react'
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
const fill = (label: string, value: string) =>
  fireEvent.change(screen.getByLabelText(label), { target: { value } })

describe('UserFormPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
    vi.unstubAllGlobals()
  })

  test('Given a new user, when details are valid, then create and return to the list', async () => {
    vi.mocked(api.createUser).mockResolvedValue(user)
    vi.mocked(api.listUsers).mockResolvedValue([user])
    const history = openCatalogRoute('/users/new')
    await screen.findByRole('heading', { name: 'New user' })
    fill('First name', 'Test')
    fill('Last name', 'Driver')
    fill('Username', 'test.driver')
    fill('CNH number', '01234567890')
    fill('Email', 'driver@example.test')
    fill('Password', 'test-password-123')
    fill('Confirm password', 'test-password-123')
    fireEvent.change(screen.getByRole('combobox', { name: 'Role' }), {
      target: { value: 'ADMIN' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Create user' }))
    await waitFor(() =>
      expect(api.createUser).toHaveBeenCalledWith(
        expect.objectContaining({ email: user.email }),
        'ADMIN',
      ),
    )
    expect(await screen.findByRole('heading', { name: 'Users' })).toBeVisible()
    expect(history.location.pathname).toBe('/users')
  })

  test('Given invalid or duplicate details, when saving, then show errors and preserve the draft', async () => {
    vi.mocked(api.createUser).mockRejectedValue(new Error('Already exists'))
    openCatalogRoute('/users/new')
    fireEvent.click(await screen.findByRole('button', { name: 'Create user' }))
    expect(await screen.findAllByRole('alert')).not.toHaveLength(0)
    expect(api.createUser).not.toHaveBeenCalled()
    fill('First name', 'Test')
    fill('Last name', 'Driver')
    fill('Username', 'test.driver')
    fill('CNH number', '01234567890')
    fill('Email', 'driver@example.test')
    fill('Password', 'test-password-123')
    fill('Confirm password', 'test-password-123')
    fireEvent.click(screen.getByRole('button', { name: 'Create user' }))
    expect(await screen.findByText('Already exists')).toBeVisible()
    expect(screen.getByLabelText('Email')).toHaveValue('driver@example.test')
  })

  test('Given an existing driver, when editing, then save a replacement without a password', async () => {
    vi.mocked(api.getUser).mockResolvedValue(user)
    vi.mocked(api.updateUser).mockResolvedValue({ ...user, firstName: 'Updated' })
    openCatalogRoute('/users/user-1/edit')
    await screen.findByRole('heading', { name: 'Edit user' })
    expect(screen.queryByLabelText('Password')).toBeNull()
    fill('First name', 'Updated')
    fireEvent.change(screen.getByRole('combobox', { name: 'Role' }), {
      target: { value: 'ADMIN' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Save user' }))
    expect(await screen.findByRole('heading', { name: 'Updated Driver' })).toBeVisible()
    expect(api.updateUser).toHaveBeenCalledWith(
      'user-1',
      expect.objectContaining({ firstName: 'Updated', role: 'ADMIN' }),
    )
  })

  test('Given a role conflict, when saving, then keep the edited role and show the error', async () => {
    vi.mocked(api.getUser).mockResolvedValue({ ...user, role: 'ADMIN' })
    vi.mocked(api.updateUser).mockRejectedValue(new Error('Cannot remove your own admin role'))
    openCatalogRoute('/users/user-1/edit')
    await screen.findByRole('heading', { name: 'Edit user' })
    fireEvent.change(screen.getByRole('combobox', { name: 'Role' }), {
      target: { value: 'USER' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Save user' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Cannot remove your own admin role')
    expect(screen.getByRole('combobox', { name: 'Role' })).toHaveValue('USER')
  })
})
