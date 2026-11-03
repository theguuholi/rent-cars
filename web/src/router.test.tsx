import { act, fireEvent, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { openCatalogRoute } from './test/open-catalog-route'

afterEach(() => vi.unstubAllGlobals())

it('supports navigation and browser history between catalogs', async () => {
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
})
