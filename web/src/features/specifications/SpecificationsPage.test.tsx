import { fireEvent, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { renderWithQuery } from '../../test/render-with-query'
import { createMemoryHistory, RouterProvider } from '@tanstack/react-router'
import { createAppRouter } from '../../router'
import { act } from '@testing-library/react'
import { deleteSpecification, listSpecifications, saveSpecification } from './specifications-api'

vi.mock('./specifications-api', () => ({
  deleteSpecification: vi.fn(),
  listSpecifications: vi.fn(),
  saveSpecification: vi.fn(),
}))
const list = vi.mocked(listSpecifications)
const save = vi.mocked(saveSpecification)
const remove = vi.mocked(deleteSpecification)
const item = { id: 'gps-id', name: 'GPS', description: 'Navigation' }
async function click(name: string) {
  await act(async () =>
    fireEvent.click(screen.queryByRole('button', { name }) ?? screen.getByRole('link', { name })),
  )
}
async function open(path = '/specifications') {
  const history = createMemoryHistory({ initialEntries: [path] })
  const router = createAppRouter(history)
  await act(async () => {
    renderWithQuery(<RouterProvider router={router} />)
    await router.load()
  })
  return history
}
const fill = (field: string, value: string) =>
  fireEvent.change(screen.getByLabelText(field), { target: { value } })
beforeEach(() => vi.resetAllMocks())

it('creates from an empty catalog and refreshes the list with the saved data', async () => {
  list.mockResolvedValueOnce([]).mockResolvedValue([item])
  save.mockResolvedValue(item)
  await open()
  expect(await screen.findByText('No specifications yet.')).toBeVisible()
  await click('New specification')
  expect(screen.getByLabelText('Name')).toBeRequired()
  expect(screen.getByLabelText('Description')).toBeRequired()
  fill('Name', ' gps ')
  fill('Description', ' Navigation ')
  await click('Save specification')
  expect(await screen.findByText('GPS')).toBeVisible()
  expect(save).toHaveBeenCalledWith({ name: 'gps', description: 'Navigation' }, undefined)
  expect(screen.getByText('Specification saved.')).toBeVisible()
})

it('prefills an edit, preserves input after a conflict and accepts a retry', async () => {
  list.mockResolvedValueOnce([item]).mockResolvedValue([{ ...item, name: 'SAT NAV' }])
  save.mockRejectedValueOnce(new Error('Name already exists')).mockResolvedValue(item)
  await open()
  await screen.findByText('GPS')
  await click('Edit GPS')
  expect(screen.getByLabelText('Description')).toHaveValue('Navigation')
  fill('Name', 'abs')
  await click('Save specification')
  expect(await screen.findByRole('alert')).toHaveTextContent('Name already exists')
  expect(screen.getByLabelText('Name')).toHaveValue('abs')
  fill('Name', 'sat nav')
  await click('Save specification')
  expect(await screen.findByText('SAT NAV')).toBeVisible()
  expect(save).toHaveBeenLastCalledWith({ name: 'sat nav', description: 'Navigation' }, item.id)
})

it('allows cancelling the editor or deletion and deletes only after confirmation', async () => {
  list.mockResolvedValueOnce([item]).mockResolvedValue([])
  remove.mockResolvedValue(undefined)
  await open()
  await screen.findByText('GPS')
  await click('Edit GPS')
  fill('Name', 'unsaved')
  await click('Cancel')
  expect(save).not.toHaveBeenCalled()
  await click('Delete GPS')
  await click('Cancel')
  expect(remove).not.toHaveBeenCalled()
  await click('Delete GPS')
  await click('Confirm deletion')
  expect(await screen.findByText('No specifications yet.')).toBeVisible()
  expect(remove).toHaveBeenCalledWith(item.id)
  expect(screen.getByText('Specification deleted.')).toBeVisible()
})

it('disables repeated submissions while a save is in progress', async () => {
  list.mockResolvedValue([])
  let finish!: (saved: typeof item) => void
  save.mockReturnValue(
    new Promise((resolve) => {
      finish = resolve
    }),
  )
  await open()
  await screen.findByText('No specifications yet.')
  await click('New specification')
  fill('Name', 'gps')
  fill('Description', 'Navigation')
  await click('Save specification')
  expect(await screen.findByRole('button', { name: 'Saving…' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
  finish(item)
  await waitFor(() =>
    expect(screen.queryByRole('button', { name: 'Saving…' })).not.toBeInTheDocument(),
  )
})

it('recovers from a list error', async () => {
  list.mockRejectedValueOnce(new Error('Offline')).mockResolvedValue([item])
  await open()
  expect(await screen.findByRole('alert')).toHaveTextContent('Could not load specifications')
  await click('Try again')
  expect(await screen.findByText('Navigation')).toBeVisible()
})

it('refreshes stale rows after a failed deletion is dismissed', async () => {
  list.mockResolvedValueOnce([item]).mockResolvedValue([])
  remove.mockRejectedValue(new Error('This specification no longer exists. Refresh the catalog.'))
  await open()
  await screen.findByText('GPS')
  await click('Delete GPS')
  await click('Confirm deletion')
  expect(await screen.findByRole('alert')).toHaveTextContent('no longer exists')
  await click('Cancel')
  expect(await screen.findByText('No specifications yet.')).toBeVisible()
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})

it('validates required fields and the normalized name length before submitting', async () => {
  list.mockResolvedValue([])
  await open()
  await screen.findByText('No specifications yet.')
  await click('New specification')
  await click('Save specification')
  expect(await screen.findByText('Enter a name.')).toBeVisible()
  expect(screen.getByText('Enter a description.')).toBeVisible()
  fill('Name', 'ß'.repeat(128))
  fill('Description', '   ')
  expect(
    await screen.findByText('Use at most 255 characters after uppercase conversion.'),
  ).toBeVisible()
  expect(screen.getByLabelText('Description')).toHaveAttribute('aria-invalid', 'true')
  await click('Save specification')
  expect(save).not.toHaveBeenCalled()
})

it('supports refresh at an edit URL, back navigation and non-destructive delete links', async () => {
  list.mockResolvedValue([item])
  const history = await open()
  await screen.findByRole('link', { name: 'Edit GPS' })
  await click('Edit GPS')
  expect(history.location.pathname).toBe('/specifications/gps-id/edit')
  expect(await screen.findByLabelText('Name')).toHaveValue('GPS')
  await act(async () => history.back())
  await click('Delete GPS')
  expect(history.location.pathname).toBe('/specifications/gps-id/delete')
  expect(await screen.findByText('Delete GPS?')).toBeVisible()
  expect(remove).not.toHaveBeenCalled()
})
it.each(['edit', 'delete'])('handles a missing specification on direct %s links', async (mode) => {
  list.mockResolvedValue([])
  await open('/specifications/missing/' + mode)
  expect(await screen.findByRole('alert')).toHaveTextContent('Specification not found')
  await click('Back to specifications')
  expect(await screen.findByRole('link', { name: 'New specification' })).toBeVisible()
})
it('opens new directly without fetching an individual specification', async () => {
  list.mockResolvedValue([])
  const history = await open('/specifications/new')
  expect(await screen.findByLabelText('Name')).toHaveValue('')
  await click('Cancel')
  expect(history.location.pathname).toBe('/specifications')
})
