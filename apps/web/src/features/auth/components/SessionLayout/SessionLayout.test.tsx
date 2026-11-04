import { RouterProvider, createMemoryHistory } from '@tanstack/react-router'
import { createAppRouter } from '../../../../router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, fireEvent, screen, waitFor, render } from '@testing-library/react'
import { describe, expect, test as scenario, vi } from 'vitest'
import { openCatalogRoute, adminSession } from '../../../../test/open-catalog-route'
import { session } from '../../session'
import { signIn } from '../../auth-api'
import { car, categories, specifications } from '../../../../test/car-fixtures'
vi.mock('../../auth-api')
describe('Session transitions and navigation', () => {
  scenario(
    'Given an unsaved car name, when both catalogs refetch after access expiry, then one renewal restores both lists and preserves the draft',
    async () => {
      localStorage.setItem('rent-cars.login', 'login')
      const client = new QueryClient()
      const history = openCatalogRoute('/cars/second/edit', client)
      let expired = false
      const calls = vi.mocked(fetch)
      calls.mockImplementation(async (url, init) => {
        if (url === '/api/cars/second') return Response.json(car)
        if (url === '/api/csrf') return new Response(null, { headers: { 'X-XSRF-TOKEN': 'proof' } })
        if (url === '/api/sessions/refresh') return Response.json({ accessToken: 'new' })
        if (url === '/api/sessions/me') return Response.json(adminSession.user)
        if (expired && new Headers(init?.headers).get('Authorization') !== 'Bearer new')
          return new Response(null, { status: 401 })
        return Response.json(url === '/api/categories' ? categories : specifications)
      })
      fireEvent.change(await screen.findByLabelText('Name'), { target: { value: 'Unsaved name' } })
      expired = true
      await act(async () => {
        await Promise.all(
          ['categories', 'specifications'].map((key) =>
            client.invalidateQueries({ queryKey: [key] }),
          ),
        )
      })
      expect(history.location.pathname).toBe('/cars/second/edit')
      expect(screen.getByLabelText('Name')).toHaveValue('Unsaved name')
      expect(calls.mock.calls.filter(([url]) => url === '/api/sessions/refresh')).toHaveLength(1)
    },
  )
  scenario(
    'Given logout temporarily fails, when the user retries, then the app displays the failure before clearing the session on confirmation',
    async () => {
      localStorage.setItem('rent-cars.login', 'login')
      openCatalogRoute('/')
      const logout = await screen.findByRole('button', {
        name: 'Sign out ' + adminSession.user.email,
      })
      vi.mocked(fetch).mockRejectedValueOnce(new Error('Offline'))
      fireEvent.click(logout)
      expect(await screen.findByRole('alert')).toHaveTextContent('Could not sign out. Try again.')
      vi.mocked(fetch)
        .mockResolvedValueOnce(new Response(null, { headers: { 'X-XSRF-TOKEN': 'proof' } }))
        .mockResolvedValueOnce(new Response(null, { status: 204 }))
      fireEvent.click(logout)
      await screen.findByRole('link', { name: 'Sign in' })
      expect(screen.queryByRole('alert')).toBeNull()
    },
  )
  scenario(
    'Given a signed-in admin, when opening the car editor, then both catalog requests carry the session bearer and the form becomes available',
    async () => {
      openCatalogRoute('/cars/second/edit')
      const calls = vi.mocked(fetch)
      calls.mockImplementation(async (url) => {
        if (url === '/api/cars/second') return Response.json(car)
        if (url === '/api/categories') return Response.json(categories)
        if (url === '/api/specifications') return Response.json(specifications)
        throw new Error('Unexpected URL ' + url)
      })
      expect(await screen.findByLabelText('Name')).toHaveValue(car.name)
      for (const url of ['/api/categories', '/api/specifications']) {
        expect(calls).toHaveBeenCalledWith(url, {
          headers: { Authorization: 'Bearer test-admin' },
          signal: expect.any(AbortSignal),
        })
      }
      expect(screen.getByRole('button', { name: 'Save car' })).toBeEnabled()
    },
  )
  scenario(
    'Given a replacement Query client, when identity changes, then remove data from the current provider and release the old subscription',
    async () => {
      const oldClient = new QueryClient(),
        nextClient = new QueryClient()
      const router = createAppRouter(createMemoryHistory({ initialEntries: ['/'] }))
      const app = (client: QueryClient) => (
        <QueryClientProvider client={client}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      )
      const view = render(app(oldClient))
      await screen.findByRole('navigation')
      view.rerender(app(nextClient))
      oldClient.setQueryData(['private'], 'old')
      nextClient.setQueryData(['private'], 'new')
      await act(async () => session.set(adminSession))
      expect(oldClient.getQueryData(['private'])).toBe('old')
      expect(nextClient.getQueryData(['private'])).toBeUndefined()
    },
  )
  scenario.each(['/categories', '/specifications', '/cars/new'])(
    'Given an account with stale permissions, when the API refuses %s, then show access denial without signing out',
    async (path) => {
      const history = openCatalogRoute(path)
      vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 403 }))
      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Your account cannot perform this action.',
      )
      expect(session.read()).toBe(adminSession)
      expect(history.location.pathname).toBe(path)
    },
  )
  scenario(
    'Given an expired token, when a protected read returns 401, then clear the session and reopen login for that route',
    async () => {
      const history = openCatalogRoute('/categories')
      vi.mocked(fetch).mockResolvedValueOnce(new Response(null, { status: 401 }))
      expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeVisible()
      expect(history.location.state.afterLogin).toBe('/categories')
      expect(session.read()).toBeNull()
    },
  )
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
