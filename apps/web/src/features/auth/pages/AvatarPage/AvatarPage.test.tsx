import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, test as scenario, vi } from 'vitest'
import { QueryClient } from '@tanstack/react-query'
import { session } from '../../session'
import { avatarRoute } from '../../routes'
import { openCatalogRoute } from '../../../../test/open-catalog-route'
import { readAvatar, saveAvatar } from '../../avatar-api'
vi.mock('../../avatar-api')
describe('AvatarPage', () => {
  const create = vi.fn(),
    revoke = vi.fn()
  beforeEach(() => {
    vi.mocked(readAvatar).mockResolvedValue(null)
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      writable: true,
      value: create,
    })
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      writable: true,
      value: revoke,
    })
    create.mockImplementation(() => 'blob:' + create.mock.calls.length)
  })
  afterEach(() => {
    vi.restoreAllMocks()
    vi.resetAllMocks()
    vi.unstubAllGlobals()
  })
  scenario(
    'Given a guest, when the route guard runs before rendering, then replace the URL with login',
    () => {
      session.set(null)
      const guard = avatarRoute.options.beforeLoad as (options: {
        context: { session: typeof session }
      }) => void
      expect(() => guard({ context: { session } })).toThrow(
        expect.objectContaining({
          options: expect.objectContaining({ to: '/login', replace: true }),
        }),
      )
    },
  )
  scenario(
    'Given an upload pending on a page the user leaves, when it completes, then preserve the cache of the next screen',
    async () => {
      const client = new QueryClient()
      const history = openCatalogRoute('/account/avatar', client)
      const input = await screen.findByLabelText('Profile image')
      fireEvent.change(input, {
        target: { files: { item: () => new File(['data'], 'avatar.png') } },
      })
      let finish!: () => void
      vi.mocked(saveAvatar).mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finish = resolve
          }),
      )
      fireEvent.click(screen.getByRole('button', { name: 'Save avatar' }))
      await waitFor(() => expect(input).toBeDisabled())
      await act(async () => history.push('/cars'))
      const invalidate = vi.spyOn(client, 'invalidateQueries')
      await act(async () => finish())
      expect(invalidate).not.toHaveBeenCalled()
    },
  )
  scenario(
    'Given an anonymous visitor, when opening the avatar URL, then require sign-in without loading private data',
    async () => {
      const history = openCatalogRoute('/account/avatar', undefined, null)
      expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeVisible()
      expect(history.location.pathname).toBe('/login')
      expect(readAvatar).not.toHaveBeenCalled()
      expect(screen.queryByRole('link', { name: 'My avatar' })).toBeNull()
    },
  )
  scenario(
    'Given a loading failure, when the driver retries, then show the empty avatar editor',
    async () => {
      let reject!: (error: Error) => void
      vi.mocked(readAvatar).mockImplementationOnce(
        () =>
          new Promise((_, fail) => {
            reject = fail
          }),
      )
      openCatalogRoute('/account/avatar')
      expect(await screen.findByRole('status')).toHaveTextContent('Loading avatar…')
      await act(async () => reject(new Error('Offline')))
      expect(await screen.findByRole('alert')).toHaveTextContent('Offline Try again')
      fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
      const file = await screen.findByLabelText('Profile image')
      expect(file).toHaveAttribute('type', 'file')
      expect(file).toHaveAttribute('accept', 'image/jpeg,image/png')
      expect(screen.getByRole('heading', { name: 'My avatar' })).toBeVisible()
      expect(screen.getByRole('link', { name: 'My avatar' })).toHaveAttribute(
        'href',
        '/account/avatar',
      )
      expect(screen.getByText('Account')).toBeVisible()
      expect(screen.getByText('Choose a JPEG or PNG up to 5 MiB and 4 megapixels.')).toBeVisible()
      expect(document.title).toBe('My avatar | Rent Cars')
      expect(screen.queryByRole('img')).toBeNull()
      expect(screen.queryByText('Remove avatar')).toBeNull()
      expect(screen.getByRole('button', { name: 'Save avatar' })).toBeDisabled()
    },
  )
  scenario(
    'Given a selected preview, when upload fails and then succeeds, then preserve it through retry and reload the stored avatar',
    async () => {
      const client = new QueryClient()
      const history = openCatalogRoute('/account/avatar', client)
      const invalidate = vi.spyOn(client, 'invalidateQueries')
      const input = await screen.findByLabelText('Profile image')
      const file = new File(['data'], 'photo.png', { type: 'image/png' })
      const select = (files: File[] | null) =>
        fireEvent.change(input, { target: { files: files && { item: () => files[0] ?? null } } })
      select([file])
      expect(await screen.findByRole('img')).toHaveAttribute('src', 'blob:1')
      expect(screen.getByRole('img')).toHaveAttribute('alt', 'Avatar preview')
      vi.mocked(saveAvatar).mockRejectedValueOnce(new Error('Storage unavailable'))
      fireEvent.click(screen.getByRole('button', { name: 'Save avatar' }))
      expect(await screen.findByRole('alert')).toHaveTextContent('Storage unavailable')
      expect(saveAvatar).toHaveBeenCalledExactlyOnceWith(file, expect.any(Object))
      expect(screen.getByRole('img')).toHaveAttribute('src', 'blob:1')
      let done!: () => void
      vi.mocked(saveAvatar).mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            done = resolve
          }),
      )
      const stored = new Blob(['new'], { type: 'image/png' })
      vi.mocked(readAvatar).mockResolvedValue(stored)
      fireEvent.click(screen.getByRole('button', { name: 'Save avatar' }))
      await waitFor(() => expect(input).toBeDisabled())
      expect(screen.getByRole('button', { name: 'Save avatar' })).toBeDisabled()
      await act(async () => done())
      expect(await screen.findByText('Avatar updated.')).toBeVisible()
      expect(invalidate).toHaveBeenCalledExactlyOnceWith({ queryKey: ['my-avatar'] })
      await waitFor(() => expect(create).toHaveBeenCalledWith(stored))
      expect(revoke).toHaveBeenCalledWith('blob:1')
      select([file])
      expect(screen.queryByText('Avatar updated.')).toBeNull()
      select([])
      select(null)
      expect(screen.getByRole('button', { name: 'Save avatar' })).toBeDisabled()
      await act(async () => history.push('/cars'))
      expect(revoke).toHaveBeenCalledWith(create.mock.results.at(-1)!.value)
    },
  )
  scenario(
    'Given a stored avatar, when removing it, then disable concurrent changes and show an empty editor after reloading',
    async () => {
      vi.mocked(readAvatar).mockResolvedValueOnce(new Blob(['stored']))
      openCatalogRoute('/account/avatar')
      const remove = await screen.findByRole('button', { name: 'Remove avatar' })
      await screen.findByRole('img')
      let finish!: () => void
      vi.mocked(saveAvatar).mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finish = resolve
          }),
      )
      fireEvent.click(remove)
      await waitFor(() => expect(remove).toBeDisabled())
      expect(saveAvatar).toHaveBeenCalledExactlyOnceWith(null, expect.any(Object))
      await act(async () => finish())
      await waitFor(() => expect(screen.queryByRole('img')).toBeNull())
      expect(screen.queryByRole('button', { name: 'Remove avatar' })).toBeNull()
    },
  )
})
