import { screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { openCatalogRoute } from '../../test/open-catalog-route'

afterEach(() => vi.unstubAllGlobals())

it('opens the categories URL directly and requests only the category catalog', async () => {
  openCatalogRoute('/categories')
  expect(await screen.findByText('No vehicle categories available.')).toBeVisible()
  expect(fetch).toHaveBeenCalledExactlyOnceWith('/api/categories', {
    signal: expect.any(AbortSignal),
  })
})
