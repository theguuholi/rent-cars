import { screen } from '@testing-library/react'
import { afterEach, expect, describe, test as scenario, vi } from 'vitest'
import { openCatalogRoute } from '../../test/open-catalog-route'

describe('Specifications routes', () => {
  afterEach(() => vi.unstubAllGlobals())

  scenario.each([
    ['/specifications', 'link', 'New specification'],
    ['/specifications/new', 'group', 'New specification'],
    ['/specifications/unknown-id/edit', 'link', 'Back to specifications'],
    ['/specifications/unknown-id/delete', 'link', 'Back to specifications'],
  ])(
    'Given a saved URL %s, when opened directly, then the expected control appears without a write request',
    async (path, role, name) => {
      openCatalogRoute(path)
      expect(await screen.findByRole(role, { name })).toBeVisible()
      await screen.findByText('No specifications yet.')
      expect(fetch).toHaveBeenCalledExactlyOnceWith('/api/specifications', {
        signal: expect.any(AbortSignal),
      })
    },
  )
})
