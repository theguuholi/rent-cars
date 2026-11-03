import { QueryClient } from '@tanstack/react-query'
import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import { describe, expect, test as scenario, vi } from 'vitest'
import { openCatalogRoute, adminSession } from '../../../../test/open-catalog-route'
import { session } from '../../session'
import { signIn } from '../../auth-api'
vi.mock('../../auth-api')
describe('Session transitions and navigation', () => {
  scenario.each([
    '/categories',
    '/specifications',
    '/specifications/new',
    '/specifications/x/edit',
    '/specifications/x/delete',
    '/cars/new',
    '/cars/x/edit',
    '/cars/x/delete',
  ])(
    'Given a guest opening %s, when the guard runs, then login opens without fetching protected data',
    async (path) => {
      const history = openCatalogRoute(path, undefined, null)
      expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeVisible()
      expect(history.location.pathname).toBe('/login')
      expect(history.location.state.afterLogin).toBe(path)
      expect(history.length).toBe(1)
      expect(fetch).not.toHaveBeenCalled()
    },
  )
  scenario(
    'Given a guest opening an admin URL, when admin login succeeds, then return to the originally requested catalog',
    async () => {
      vi.mocked(signIn).mockResolvedValue(adminSession)
      const history = openCatalogRoute('/categories', undefined, null)
      const form = (await screen.findByRole('button', { name: 'Sign in' })).closest('form')!
      fireEvent.submit(form)
      expect(await screen.findByRole('heading', { name: 'Vehicle categories' })).toBeVisible()
      expect(history.location.pathname).toBe('/categories')
      expect(fetch).toHaveBeenCalledWith(
        '/api/categories',
        expect.objectContaining({ headers: { Authorization: 'Bearer test-admin' } }),
      )
    },
  )
  scenario(
    'Given an admin on a protected page with pending private data, when signing out, then cancel the pending request, clear the cache and return to login',
    async () => {
      const client = new QueryClient()
      openCatalogRoute('/categories', client, adminSession)
      await screen.findByText('No vehicle categories available.')
      client.setQueryData(['specifications'], ['private'])
      let signal!: AbortSignal
      void client.prefetchQuery({
        queryKey: ['pending-private'],
        queryFn: (ctx) => {
          signal = ctx.signal
          return new Promise(() => {})
        },
      })
      fireEvent.click(screen.getByRole('button', { name: 'Sign out ' + adminSession.user.email }))
      await screen.findByRole('heading', { name: 'Sign in' })
      expect(signal.aborted).toBe(true)
      expect(client.getQueryData(['specifications'])).toBeUndefined()
      expect(client.getQueryData(['categories'])).toBeUndefined()
      expect(screen.queryByRole('link', { name: 'Categories' })).not.toBeInTheDocument()
    },
  )
  scenario(
    'Given a public fleet and an ordinary driver, when navigating, then only public links and fleet actions are visible',
    async () => {
      openCatalogRoute('/cars', undefined, {
        ...adminSession,
        user: { ...adminSession.user, role: 'USER' },
      })
      expect(await screen.findByText('No cars yet.')).toBeVisible()
      for (const name of ['New car', 'Categories', 'Specifications'])
        expect(screen.queryByRole('link', { name })).not.toBeInTheDocument()
      await act(async () => session.set(adminSession))
      await waitFor(() => expect(screen.getByRole('link', { name: 'New car' })).toBeVisible())
    },
  )
})
