import { screen } from '@testing-library/react'
import { afterEach, expect, describe, test as scenario, vi } from 'vitest'
import { openCatalogRoute } from '../../test/open-catalog-route'

describe('Categories routes', () => {
  afterEach(() => vi.unstubAllGlobals())

  scenario(
    'Given the categories URL, when opened directly, then only the category catalog is requested',
    async () => {
      openCatalogRoute('/categories')
      expect(await screen.findByText('No vehicle categories available.')).toBeVisible()
      expect(fetch).toHaveBeenCalledExactlyOnceWith('/api/categories', {
        signal: expect.any(AbortSignal),
      })
    },
  )
})
