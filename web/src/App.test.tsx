import { act, fireEvent, screen } from '@testing-library/react'
import { createMemoryHistory, RouterProvider } from '@tanstack/react-router'
import { afterEach, expect, it, vi } from 'vitest'
import { createAppRouter } from './router'
import { renderWithQuery } from './test/render-with-query'

afterEach(() => vi.unstubAllGlobals())

function open(path: string) {
  const history = createMemoryHistory({ initialEntries: [path] })
  const router = createAppRouter(history)
  vi.stubGlobal(
    'fetch',
    vi.fn().mockImplementation(async () => Response.json([])),
  )
  renderWithQuery(<RouterProvider router={router} />)
  return history
}

it('shows only the menu on the home page', async () => {
  open('/')
  expect(await screen.findByRole('navigation')).toBeVisible()
  expect(screen.getByRole('link', { name: 'Categories' })).toHaveAttribute('href', '/categories')
  expect(screen.getByRole('link', { name: 'Specifications' })).toHaveAttribute(
    'href',
    '/specifications',
  )
  expect(screen.queryByRole('heading', { level: 2 })).not.toBeInTheDocument()
  expect(fetch).not.toHaveBeenCalled()
})

it.each([
  ['/categories', 'No vehicle categories available.'],
  ['/specifications', 'No specifications yet.'],
])('opens %s directly', async (path, emptyMessage) => {
  open(path)
  expect(await screen.findByText(emptyMessage)).toBeVisible()
  expect(fetch).toHaveBeenCalledTimes(1)
})

it('supports navigation and browser history between catalogs', async () => {
  const history = open('/categories')
  await screen.findByText('No vehicle categories available.')
  fireEvent.click(screen.getByRole('link', { name: 'Specifications' }))
  expect(await screen.findByText('No specifications yet.')).toBeVisible()
  expect(history.location.pathname).toBe('/specifications')
  await act(async () => history.back())
  expect(await screen.findByText('No vehicle categories available.')).toBeVisible()
})
