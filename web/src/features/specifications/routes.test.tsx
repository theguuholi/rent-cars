import { screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { openCatalogRoute } from '../../test/open-catalog-route'

afterEach(() => vi.unstubAllGlobals())

it.each([
  ['/specifications', 'link', 'New specification'],
  ['/specifications/new', 'group', 'New specification'],
  ['/specifications/unknown-id/edit', 'link', 'Back to specifications'],
  ['/specifications/unknown-id/delete', 'link', 'Back to specifications'],
])('opens %s directly without mutating data', async (path, role, name) => {
  openCatalogRoute(path)
  expect(await screen.findByRole(role, { name })).toBeVisible()
  await screen.findByText('No specifications yet.')
  expect(fetch).toHaveBeenCalledExactlyOnceWith('/api/specifications', {
    signal: expect.any(AbortSignal),
  })
})
