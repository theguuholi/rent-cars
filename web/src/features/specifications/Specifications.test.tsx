import { fireEvent, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { renderWithQuery } from '../../test/render-with-query'
import { Specifications } from './Specifications'
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
const click = (name: string) => fireEvent.click(screen.getByRole('button', { name }))
const fill = (field: string, value: string) =>
  fireEvent.change(screen.getByLabelText(field), { target: { value } })
beforeEach(() => vi.resetAllMocks())

it('creates from an empty catalog and refreshes the list with the saved data', async () => {
  list.mockResolvedValueOnce([]).mockResolvedValue([item])
  save.mockResolvedValue(item)
  renderWithQuery(<Specifications />)
  expect(screen.getByRole('status')).toHaveTextContent('Loading specifications')
  expect(await screen.findByText('No specifications yet.')).toBeVisible()
  click('New specification')
  expect(screen.getByLabelText('Name')).toBeRequired()
  expect(screen.getByLabelText('Description')).toBeRequired()
  fill('Name', ' gps ')
  fill('Description', ' Navigation ')
  click('Save specification')
  expect(await screen.findByText('GPS')).toBeVisible()
  expect(save).toHaveBeenCalledWith({ name: 'gps', description: 'Navigation' }, undefined)
  expect(screen.getByText('Specification saved.')).toBeVisible()
})

it('prefills an edit, preserves input after a conflict and accepts a retry', async () => {
  list.mockResolvedValueOnce([item]).mockResolvedValue([{ ...item, name: 'SAT NAV' }])
  save.mockRejectedValueOnce(new Error('Name already exists')).mockResolvedValue(item)
  renderWithQuery(<Specifications />)
  await screen.findByText('GPS')
  click('Edit GPS')
  expect(screen.getByLabelText('Description')).toHaveValue('Navigation')
  fill('Name', 'abs')
  click('Save specification')
  expect(await screen.findByRole('alert')).toHaveTextContent('Name already exists')
  expect(screen.getByLabelText('Name')).toHaveValue('abs')
  fill('Name', 'sat nav')
  click('Save specification')
  expect(await screen.findByText('SAT NAV')).toBeVisible()
  expect(save).toHaveBeenLastCalledWith({ name: 'sat nav', description: 'Navigation' }, item.id)
})

it('allows cancelling the editor or deletion and deletes only after confirmation', async () => {
  list.mockResolvedValueOnce([item]).mockResolvedValue([])
  remove.mockResolvedValue(undefined)
  renderWithQuery(<Specifications />)
  await screen.findByText('GPS')
  click('Edit GPS')
  fill('Name', 'unsaved')
  click('Cancel')
  expect(save).not.toHaveBeenCalled()
  click('Delete GPS')
  click('Cancel')
  expect(remove).not.toHaveBeenCalled()
  click('Delete GPS')
  click('Confirm deletion')
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
  renderWithQuery(<Specifications />)
  await screen.findByText('No specifications yet.')
  click('New specification')
  fill('Name', 'gps')
  fill('Description', 'Navigation')
  click('Save specification')
  expect(await screen.findByRole('button', { name: 'Saving…' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
  finish(item)
  await waitFor(() =>
    expect(screen.queryByRole('button', { name: 'Saving…' })).not.toBeInTheDocument(),
  )
})

it('recovers from a list error', async () => {
  list.mockRejectedValueOnce(new Error('Offline')).mockResolvedValue([item])
  renderWithQuery(<Specifications />)
  expect(await screen.findByRole('alert')).toHaveTextContent('Could not load specifications')
  click('Try again')
  expect(await screen.findByText('Navigation')).toBeVisible()
})

it('refreshes stale rows after a failed deletion is dismissed', async () => {
  list.mockResolvedValueOnce([item]).mockResolvedValue([])
  remove.mockRejectedValue(new Error('This specification no longer exists. Refresh the catalog.'))
  renderWithQuery(<Specifications />)
  await screen.findByText('GPS')
  click('Delete GPS')
  click('Confirm deletion')
  expect(await screen.findByRole('alert')).toHaveTextContent('no longer exists')
  click('Cancel')
  expect(await screen.findByText('No specifications yet.')).toBeVisible()
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})
