import { adminSession } from '../../../../test/open-catalog-route'
import { session } from '../../../auth/session'
import { QueryClient } from '@tanstack/react-query'
import { createMemoryHistory, RouterProvider } from '@tanstack/react-router'
import { act, fireEvent, screen } from '@testing-library/react'
import { beforeEach, describe, expect, test as scenario, vi } from 'vitest'
import { createAppRouter } from '../../../../router'
import { renderWithQuery } from '../../../../test/render-with-query'
import { deleteSpecification, listSpecifications } from '../../specifications-api'

vi.mock('../../specifications-api', () => ({
  deleteSpecification: vi.fn(),
  listSpecifications: vi.fn(),
  saveSpecification: vi.fn(),
}))

const cached = { id: 'gps-id', name: 'GPS', description: 'Navigation' }

async function openConfirmation() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  client.setQueryData(['specifications'], [cached])
  client.setQueryData(['categories'], [])
  const history = createMemoryHistory({
    initialEntries: ['/', '/specifications/gps-id/delete'],
  })
  session.set(adminSession)
  const router = createAppRouter(history)
  await act(async () => {
    renderWithQuery(<RouterProvider router={router} />, client)
    await router.load()
  })
  expect(await screen.findByRole('heading', { name: 'Delete GPS?' })).toBeVisible()
  expect(listSpecifications).not.toHaveBeenCalled()
  return { client, history }
}

async function click(name: string) {
  await act(async () => fireEvent.click(screen.getByRole('button', { name })))
}

describe('SpecificationsPage catalog cache and browser history', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(listSpecifications).mockResolvedValue([])
  })

  scenario(
    'Given a cached specification, when deletion succeeds, then only specifications refresh and Back skips the completed confirmation',
    async () => {
      vi.mocked(deleteSpecification).mockResolvedValue(undefined)
      const { client, history } = await openConfirmation()
      await click('Confirm deletion')
      expect(await screen.findByText('Specification deleted.')).toBeVisible()
      expect(await screen.findByText('No specifications yet.')).toBeVisible()
      expect(client.getQueryState(['categories'])?.isInvalidated).toBe(false)
      await act(async () => history.back())
      expect(history.location.pathname).toBe('/')
    },
  )

  scenario(
    'Given a stale specification after a failed deletion, when the user cancels, then only specifications refresh',
    async () => {
      vi.mocked(deleteSpecification).mockRejectedValue(new Error('Already removed'))
      const { client } = await openConfirmation()
      await click('Confirm deletion')
      expect(await screen.findByRole('alert')).toHaveTextContent('Already removed')
      await click('Cancel')
      expect(await screen.findByText('No specifications yet.')).toBeVisible()
      expect(client.getQueryState(['categories'])?.isInvalidated).toBe(false)
    },
  )

  scenario(
    'Given a fresh cached specification, when the user cancels without deleting, then the cached row stays and Back skips the dismissed confirmation',
    async () => {
      const { history } = await openConfirmation()
      await click('Cancel')
      expect(await screen.findByRole('link', { name: 'New specification' })).toBeVisible()
      expect(screen.getByRole('rowheader', { name: 'GPS' })).toBeVisible()
      expect(listSpecifications).not.toHaveBeenCalled()
      await act(async () => history.back())
      expect(history.location.pathname).toBe('/')
    },
  )
})
