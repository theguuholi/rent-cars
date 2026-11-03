import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, describe, test as scenario, vi } from 'vitest'
import { openCatalogRoute } from './test/open-catalog-route'

describe('Catalog navigation', () => {
  afterEach(() => vi.unstubAllGlobals())

  scenario(
    'Given a visit from Categories to Specifications, when the visitor goes back and forward, then the URL and visible catalog follow history',
    async () => {
      const history = openCatalogRoute('/categories')
      await screen.findByText('No vehicle categories available.')
      fireEvent.click(screen.getByRole('link', { name: 'Specifications' }))
      expect(await screen.findByText('No specifications yet.')).toBeVisible()
      expect(history.location.pathname).toBe('/specifications')
      await act(async () => history.back())
      expect(await screen.findByText('No vehicle categories available.')).toBeVisible()
      expect(history.location.pathname).toBe('/categories')
      await act(async () => history.forward())
      expect(await screen.findByText('No specifications yet.')).toBeVisible()
      expect(history.location.pathname).toBe('/specifications')
    },
  )
})

describe('Document title', () => {
  scenario.each([
    ['/', 'Rent Cars'],
    ['/categories', 'Categories | Rent Cars'],
    ['/specifications', 'Specifications | Rent Cars'],
    ['/specifications/new', 'New specification | Rent Cars'],
    ['/specifications/123/edit', 'Edit specification | Rent Cars'],
    ['/specifications/123/delete', 'Delete specification | Rent Cars'],
    ['/unknown', 'Page not found | Rent Cars'],
  ])('Given %s, when the route opens, then the browser title is %s', async (path, title) => {
    openCatalogRoute(path)
    await screen.findByRole('navigation')
    await waitFor(() => expect(document.title).toBe(title))
  })
})
