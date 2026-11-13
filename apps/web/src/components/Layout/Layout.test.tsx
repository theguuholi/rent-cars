import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, expect, describe, test as scenario, vi } from 'vitest'
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  RouterProvider,
} from '@tanstack/react-router'
import { Layout } from './Layout'
import { openCatalogRoute } from '../../test/open-catalog-route'

describe('Application layout', () => {
  afterEach(() => vi.unstubAllGlobals())

  scenario(
    'Given a catalog with a nested editor, when its URL opens, then the tab title identifies the editor instead of its parent catalog',
    async () => {
      const root = createRootRoute({ component: Layout })
      const catalog = createRoute({
        getParentRoute: () => root,
        path: '/catalog',
        staticData: { title: 'Catalog' },
      })
      const editor = createRoute({
        getParentRoute: () => catalog,
        path: 'edit',
        staticData: { title: 'Edit entry' },
        component: () => <h2>Edit entry</h2>,
      })
      const router = createRouter({
        routeTree: root.addChildren([catalog.addChildren([editor])]),
        history: createMemoryHistory({ initialEntries: ['/catalog/edit'] }),
      })
      render(<RouterProvider router={router} />)
      expect(await screen.findByRole('heading', { name: 'Edit entry' })).toBeVisible()
      await waitFor(() => expect(document.title).toBe('Edit entry | Rent Cars'))
    },
  )

  scenario(
    'Given an active catalog, when the layout renders, then the banner offers a home link and marks the current catalog',
    async () => {
      openCatalogRoute('/categories')
      await screen.findByText('No vehicle categories available.')
      const nav = screen.getByRole('navigation', { name: 'Vehicle catalogs' })
      expect(screen.getByRole('link', { name: 'Rent Cars home' })).toHaveAttribute('href', '/')
      expect(within(nav).queryByRole('link', { name: 'My rentals' })).not.toBeInTheDocument()
      expect(within(nav).getByRole('link', { name: 'Categories' })).toHaveAttribute(
        'aria-current',
        'page',
      )
      expect(
        within(screen.getByRole('main')).getByRole('heading', { name: 'Vehicle categories' }),
      ).toBeVisible()
    },
  )

  scenario(
    'Given the specifications catalog is active, when the user follows the brand link, then only the home menu remains',
    async () => {
      const history = openCatalogRoute('/specifications')
      await screen.findByText('No specifications yet.')
      const nav = within(screen.getByRole('navigation', { name: 'Vehicle catalogs' }))
      expect(nav.getByRole('link', { name: 'Specifications' })).toHaveAttribute(
        'aria-current',
        'page',
      )
      expect(nav.getByRole('link', { name: 'Categories' })).not.toHaveAttribute('aria-current')
      fireEvent.click(screen.getByRole('link', { name: 'Rent Cars home' }))
      await waitFor(() => expect(screen.getByRole('main')).toBeEmptyDOMElement())
      expect(history.location.pathname).toBe('/')
      expect(fetch).toHaveBeenCalledTimes(1)
    },
  )
})
