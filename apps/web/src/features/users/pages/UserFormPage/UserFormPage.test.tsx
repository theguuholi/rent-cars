import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, test, vi } from 'vitest'
import { QueryClient } from '@tanstack/react-query'
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
    expect(api.updateUser).toHaveBeenCalledWith('user-1', {
      firstName: 'Updated',
      lastName: 'Driver',
      userName: 'test.driver',
      driveLicense: '01234567890',
      email: user.email,
      role: 'ADMIN',
    })
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

  test('Given an existing driver, when edit details are invalid, then keep the draft without calling the API', async () => {
    vi.mocked(api.getUser).mockResolvedValue(user)
    openCatalogRoute('/users/user-1/edit')
    await screen.findByRole('heading', { name: 'Edit user' })
    fill('First name', ' ')
    fireEvent.click(screen.getByRole('button', { name: 'Save user' }))
    expect(await screen.findByRole('alert')).toBeVisible()
    expect(screen.getByLabelText('First name')).toHaveValue(' ')
    expect(api.updateUser).not.toHaveBeenCalled()
  })

  test('Given an admin without a driver profile, when editing, then submit only account details', async () => {
    const admin = {
      ...user,
      role: 'ADMIN' as const,
      firstName: null,
      lastName: null,
      userName: null,
      driveLicense: null,
    }
    vi.mocked(api.getUser)
      .mockResolvedValueOnce(admin)
      .mockResolvedValue({ ...admin, email: 'new@example.test' })
    vi.mocked(api.updateUser).mockResolvedValue({ ...admin, email: 'new@example.test' })
    openCatalogRoute('/users/user-1/edit')
    await screen.findByRole('heading', { name: 'Edit user' })
    expect(screen.queryByLabelText('First name')).toBeNull()
    fill('Email', 'new@example.test')
    fireEvent.click(screen.getByRole('button', { name: 'Save user' }))
    expect(await screen.findByRole('heading', { name: 'new@example.test' })).toBeVisible()
    expect(api.updateUser).toHaveBeenCalledWith('user-1', {
      email: 'new@example.test',
      role: 'ADMIN',
      firstName: null,
      lastName: null,
      userName: null,
      driveLicense: null,
    })
  })
})

function fillNewUser() {
  for (const [label, value] of Object.entries({
    'First name': 'Test',
    'Last name': 'Driver',
    Username: 'test.driver',
    'CNH number': '01234567890',
    Email: user.email,
    Password: 'test-password-123',
    'Confirm password': 'test-password-123',
  }))
    fill(label, value)
}

function submitUser(label: string) {
  const event = new Event('submit', { bubbles: true, cancelable: true })
  fireEvent(screen.getByRole('button', { name: label }).closest('form')!, event)
  expect(event.defaultPrevented).toBe(true)
}

describe('UserFormPage validation and retries', () => {
  afterEach(() => {
    vi.resetAllMocks()
    vi.unstubAllGlobals()
  })

  test('Given a blank user form, when submitted, then every field starts empty and reports its own required error', async () => {
    openCatalogRoute('/users/new')
    await screen.findByLabelText('First name')
    for (const label of [
      'First name',
      'Last name',
      'Username',
      'CNH number',
      'Email',
      'Password',
      'Confirm password',
    ]) {
      expect(screen.getByLabelText(label, { exact: true })).toHaveValue('')
    }
    expect(screen.getByRole('combobox', { name: 'Role' })).toHaveValue('USER')
    submitUser('Create user')
    await waitFor(() => expect(screen.getAllByRole('alert')).toHaveLength(7))
    expect(screen.getByLabelText('First name')).toHaveAccessibleDescription(
      'Enter a first name of up to 80 characters.',
    )
    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription(
      'Enter a valid email address.',
    )
    expect(screen.getByLabelText('Confirm password')).toHaveAccessibleDescription(
      'Passwords do not match.',
    )
    expect(api.createUser).not.toHaveBeenCalled()
  })

  test.each(['create', 'edit'] as const)(
    'Given a failed %s, when retrying, then clear the server error, preserve the draft and disable duplicate submission',
    async (mode) => {
      vi.mocked(api.getUser).mockResolvedValue(user)
      const request = mode === 'create' ? vi.mocked(api.createUser) : vi.mocked(api.updateUser)
      let finish!: (result: typeof user) => void
      request.mockRejectedValueOnce('offline').mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finish = resolve
          }),
      )
      vi.mocked(api.listUsers).mockResolvedValue([user])
      openCatalogRoute(mode === 'create' ? '/users/new' : '/users/user-1/edit')
      await screen.findByLabelText('Email')
      if (mode === 'create') fillNewUser()
      const button = mode === 'create' ? 'Create user' : 'Save user'
      submitUser(button)
      expect(await screen.findByRole('alert')).toHaveTextContent('Could not save this user.')
      expect(screen.getByLabelText('Email')).toHaveValue(user.email)
      submitUser(button)
      await waitFor(() => expect(screen.getByRole('button', { name: button })).toBeDisabled())
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
      expect(request).toHaveBeenCalledTimes(2)
      await act(async () => finish(user))
    },
  )

  test.each(['create', 'edit'] as const)(
    'Given cached catalogs, when a %s succeeds, then invalidate only user lists and keep unrelated data fresh',
    async (mode) => {
      const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
      client.setQueryData(['managedUsers', 'inactive-filter'], [user])
      client.setQueryData(['cars', 'unrelated'], [])
      vi.mocked(api.getUser).mockResolvedValue(user)
      vi.mocked(api.createUser).mockResolvedValue(user)
      vi.mocked(api.updateUser).mockResolvedValue(user)
      vi.mocked(api.listUsers).mockResolvedValue([user])
      openCatalogRoute(mode === 'create' ? '/users/new' : '/users/user-1/edit', client)
      await screen.findByLabelText('Email')
      if (mode === 'create') fillNewUser()
      submitUser(mode === 'create' ? 'Create user' : 'Save user')
      await waitFor(() =>
        expect(client.getQueryState(['managedUsers', 'inactive-filter'])?.isInvalidated).toBe(true),
      )
      expect(client.getQueryState(['cars', 'unrelated'])?.isInvalidated).toBe(false)
      if (mode === 'edit')
        await waitFor(() => expect(client.getQueryData(['managedUser', user.id])).toEqual(user))
    },
  )

  test.each(['save', 'cancel'] as const)(
    'Given the loaded user identity, when choosing %s, then navigate to that user detail',
    async (action) => {
      vi.mocked(api.getUser).mockResolvedValue(user)
      vi.mocked(api.updateUser).mockResolvedValue(user)
      const history = openCatalogRoute('/users/lookup-id/edit')
      await screen.findByLabelText('Email')
      if (action === 'save') submitUser('Save user')
      else fireEvent.click(screen.getByRole('link', { name: 'Cancel' }))
      await waitFor(() => expect(history.location.pathname).toBe('/users/user-1'))
      expect(await screen.findByRole('heading', { name: 'Test Driver' })).toBeVisible()
    },
  )

  test('Given an unsaved new user, when cancelling, then return to the user list without saving', async () => {
    vi.mocked(api.listUsers).mockResolvedValue([])
    const history = openCatalogRoute('/users/new')
    fireEvent.click(await screen.findByRole('link', { name: 'Cancel' }))
    await waitFor(() => expect(history.location.pathname).toBe('/users'))
    expect(api.createUser).not.toHaveBeenCalled()
  })
})
