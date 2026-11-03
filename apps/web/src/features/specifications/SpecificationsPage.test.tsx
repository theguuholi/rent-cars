import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, expect, describe, test as scenario, vi } from 'vitest'
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
describe('SpecificationsPage', () => {
  beforeEach(() => vi.resetAllMocks())

  describe('Creating and editing', () => {
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
        expect(save).toHaveBeenLastCalledWith(
          { name: 'sat nav', description: 'Navigation' },
          item.id,
        )
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
  })

  describe('Deletion', () => {
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
      'Given a stale deletion error, when the user cancels, then the catalog refreshes and clears the alert',
      async () => {
        list.mockResolvedValueOnce([item]).mockResolvedValue([])
        remove.mockRejectedValue(
          new Error('This specification no longer exists. Refresh the catalog.'),
        )
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
  })

  describe('Catalog recovery', () => {
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
  })

  describe('Action URLs and history', () => {
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

        const panel = screen.getByRole('complementary', { name: 'Specification details' })
        const layout = panel.parentElement
        expect(layout).toHaveClass('grid', 'lg:grid-cols-[minmax(0,1fr)_22rem]')
        for (const record of [item, second]) {
          const row = screen.getByRole('row', { name: new RegExp(record.name) })
          expect(row).toHaveAttribute('data-selected', String(record.id === second.id))
          for (const action of ['Edit', 'Delete']) {
            const link = within(row).getByLabelText(action + ' ' + record.name)
            expect(link).toHaveAttribute('aria-disabled', 'true')
            expect(link).not.toHaveAttribute('href')
          }
        }

        await click('Cancel')
        expect(history.location.pathname).toBe('/specifications')
        expect(layout).not.toHaveClass('grid')
        expect(save).not.toHaveBeenCalled()
        expect(remove).not.toHaveBeenCalled()
      },
    )
  })

  describe('Catalog presentation', () => {
    scenario(
      'Given a pending catalog request, when SpecificationsPage opens, then the named region shows loading without a table or empty message',
      async () => {
        list.mockReturnValue(new Promise(() => {}))
        await open()
        const page = within(screen.getByRole('region', { name: 'Vehicle specifications' }))
        expect(page.getByRole('heading', { level: 2 })).toHaveTextContent('Vehicle specifications')
        expect(page.getByRole('status')).toHaveTextContent('Loading specifications…')
        expect(page.queryByRole('table')).not.toBeInTheDocument()
        expect(page.queryByText('No specifications yet.')).not.toBeInTheDocument()
      },
    )

    scenario(
      'Given an empty response, when loading finishes, then the empty catalog offers creation without a table or details panel',
      async () => {
        list.mockResolvedValue([])
        await open()
        expect(await screen.findByText('No specifications yet.')).toBeVisible()
        expect(screen.getByRole('link', { name: 'New specification' })).toHaveAttribute(
          'href',
          '/specifications/new',
        )
        expect(screen.queryByRole('table')).not.toBeInTheDocument()
        expect(screen.queryByRole('complementary')).not.toBeInTheDocument()
        expect(screen.queryByRole('status')).not.toBeInTheDocument()
      },
    )

    scenario(
      'Given two specifications, when the catalog renders, then each table row links its own name and description to the correct edit and delete URLs',
      async () => {
        const records = [{ id: 'abs-id', name: 'ABS', description: 'Braking' }, item]
        list.mockResolvedValue(records)
        await open()
        const table = within(await screen.findByRole('table', { name: 'Specifications' }))
        expect(table.getAllByRole('columnheader').map((cell) => cell.textContent)).toEqual([
          'Name',
          'Description',
          'Actions',
        ])
        const rows = table.getAllByRole('row').slice(1)
        expect(rows).toHaveLength(records.length)
        records.forEach((record, index) => {
          const row = within(rows[index])
          expect(row.getByRole('rowheader')).toHaveTextContent(record.name)
          expect(row.getByText(record.description)).toBeVisible()
          for (const action of ['Edit', 'Delete'])
            expect(row.getByRole('link', { name: action + ' ' + record.name })).toHaveAttribute(
              'href',
              '/specifications/' + record.id + '/' + action.toLowerCase(),
            )
        })
      },
    )
  })

  describe('Action panel transitions', () => {
    scenario.each(['edit', 'delete'])(
      'Given a saved %s URL, when the catalog is still loading, then a missing record is not reported prematurely',
      async (mode) => {
        list.mockReturnValue(new Promise(() => {}))
        await open('/specifications/gps-id/' + mode)
        expect(screen.getByRole('status')).toHaveTextContent('Loading specifications')
        const panel = within(screen.getByRole('complementary', { name: 'Specification details' }))
        expect(panel.queryByRole('alert')).not.toBeInTheDocument()
        expect(panel.queryByRole('button')).not.toBeInTheDocument()
        expect(panel.getByRole('link', { name: 'Back to specifications' })).toBeVisible()
      },
    )

    scenario(
      'Given an edit link and an offline catalog, when retry succeeds, then the original URL opens the requested editor',
      async () => {
        list.mockRejectedValueOnce(new Error('Offline')).mockResolvedValue([item])
        const history = await open('/specifications/gps-id/edit')
        expect(await screen.findByRole('alert')).toHaveTextContent('Could not load specifications.')
        expect(screen.queryByText('Specification not found.')).not.toBeInTheDocument()
        await click('Try again')
        expect(await screen.findByLabelText('Name')).toHaveValue('GPS')
        expect(history.location.pathname).toBe('/specifications/gps-id/edit')
      },
    )

    scenario(
      'Given a conflicting create request, when saving fails, then the new URL and both draft fields survive until the user cancels',
      async () => {
        list.mockResolvedValue([item])
        save.mockRejectedValue(new Error('A specification with this name already exists.'))
        const history = await open('/specifications/new')
        fill('Name', 'gps')
        fill('Description', 'Draft maps')
        await click('Save specification')
        expect(await screen.findByRole('alert')).toHaveTextContent('already exists')
        expect(history.location.pathname).toBe('/specifications/new')
        expect(screen.getByLabelText('Name')).toHaveValue('gps')
        expect(screen.getByLabelText('Description')).toHaveValue('Draft maps')
        await click('Cancel')
        expect(history.location.pathname).toBe('/specifications')
        expect(screen.queryByRole('alert')).not.toBeInTheDocument()
        expect(save).toHaveBeenCalledTimes(1)
      },
    )

    scenario(
      'Given a confirmed deletion in flight, when the API has not yet responded, then confirmation and cancellation stay disabled until the list refreshes',
      async () => {
        let finish!: () => void
        list.mockResolvedValueOnce([item]).mockResolvedValue([])
        remove.mockReturnValue(
          new Promise((resolve) => {
            finish = resolve
          }),
        )
        const history = await open('/specifications/gps-id/delete')
        await screen.findByText('Delete GPS?')
        await click('Confirm deletion')
        const confirm = screen.getByRole('button', { name: 'Confirm deletion' })
        await waitFor(() => expect(confirm).toBeDisabled())
        expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
        fireEvent.click(confirm)
        expect(remove).toHaveBeenCalledExactlyOnceWith(item.id)
        finish()
        expect(await screen.findByText('Specification deleted.')).toBeVisible()
        expect(await screen.findByText('No specifications yet.')).toBeVisible()
        expect(history.location.pathname).toBe('/specifications')
      },
    )
  })
})
