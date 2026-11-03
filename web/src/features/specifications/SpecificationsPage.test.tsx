import { fireEvent, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, test as scenario, vi } from 'vitest'
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

scenario(
  'Given an empty catalog, when a valid specification is created, then the refreshed list shows its saved name',
  async () => {
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
  },
)

scenario(
  'Given a conflicting rename, when the user corrects the draft and retries, then the updated record appears',
  async () => {
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
  },
)

scenario(
  'Given an edited draft, when the user cancels, then the catalog returns without saving',
  async () => {
    list.mockResolvedValue([item])
    const history = await open('/specifications/gps-id/edit')
    await screen.findByLabelText('Name')
    fill('Name', 'unsaved')
    await click('Cancel')
    expect(history.location.pathname).toBe('/specifications')
    expect(save).not.toHaveBeenCalled()
  },
)

scenario(
  'Given a delete confirmation, when the user cancels, then the specification stays in the catalog',
  async () => {
    list.mockResolvedValue([item])
    await open('/specifications/gps-id/delete')
    await screen.findByText('Delete GPS?')
    await click('Cancel')
    expect(await screen.findByText('GPS')).toBeVisible()
    expect(remove).not.toHaveBeenCalled()
  },
)

scenario(
  'Given a delete confirmation, when the user confirms, then the API removes that UUID and the catalog refreshes',
  async () => {
    list.mockResolvedValueOnce([item]).mockResolvedValue([])
    remove.mockResolvedValue(undefined)
    await open('/specifications/gps-id/delete')
    await screen.findByText('Delete GPS?')
    await click('Confirm deletion')
    expect(await screen.findByText('No specifications yet.')).toBeVisible()
    expect(remove).toHaveBeenCalledWith(item.id)
    expect(screen.getByText('Specification deleted.')).toBeVisible()
  },
)

scenario(
  'Given a pending save, when the editor renders, then save and cancel are disabled',
  async () => {
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
  },
)

scenario(
  'Given a failed catalog request, when the user retries successfully, then the specifications appear',
  async () => {
    list.mockRejectedValueOnce(new Error('Offline')).mockResolvedValue([item])
    await open()
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load specifications')
    await click('Try again')
    expect(await screen.findByText('Navigation')).toBeVisible()
  },
)

scenario(
  'Given a stale deletion error, when the user cancels, then the catalog refreshes and clears the alert',
  async () => {
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
  },
)

scenario(
  'Given an overlong uppercase name and a blank description, when the user submits, then the invalid draft is never saved',
  async () => {
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
  },
)

scenario(
  'Given an edit URL reached from the list, when the user goes back and opens Delete, then the confirmation URL identifies the same record',
  async () => {
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
  },
)
scenario.each(['edit', 'delete'])(
  'Given a missing UUID on a direct %s link, when the visitor returns from the missing-record alert, then the catalog opens',
  async (mode) => {
    list.mockResolvedValue([])
    await open('/specifications/missing/' + mode)
    expect(await screen.findByRole('alert')).toHaveTextContent('Specification not found')
    await click('Back to specifications')
    expect(await screen.findByRole('link', { name: 'New specification' })).toBeVisible()
  },
)
scenario(
  'Given a direct creation URL, when the user cancels the empty form, then the catalog URL is restored',
  async () => {
    list.mockResolvedValue([])
    const history = await open('/specifications/new')
    expect(await screen.findByLabelText('Name')).toHaveValue('')
    await click('Cancel')
    expect(history.location.pathname).toBe('/specifications')
  },
)

scenario.each(['edit', 'delete'])(
  'Given a saved %s URL for the second record, when opened directly, then the panel targets that UUID without a mutation',
  async (action) => {
    const second = {
      id: '9bb13a38-2b2e-4082-8bc7-8ad60af49b8c',
      name: 'ABS',
      description: 'Anti-lock braking.',
    }
    list.mockResolvedValue([item, second])
    const history = await open(`/specifications/${second.id}/${action}`)

    if (action === 'edit') {
      expect(await screen.findByLabelText('Name')).toHaveValue(second.name)
      expect(screen.getByLabelText('Description')).toHaveValue(second.description)
    } else {
      expect(await screen.findByRole('heading', { name: 'Delete ABS?' })).toBeVisible()
    }

    await click('Cancel')
    expect(history.location.pathname).toBe('/specifications')
    expect(save).not.toHaveBeenCalled()
    expect(remove).not.toHaveBeenCalled()
  },
)
