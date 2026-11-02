import { fireEvent, screen, within } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { renderWithQuery } from '../../test/render-with-query'
import { listCategories } from './categories-api'
import { CategoryList } from './CategoryList'

vi.mock('./categories-api', () => ({ listCategories: vi.fn() }))

const load = vi.mocked(listCategories)
beforeEach(() => load.mockReset())

it('shows loading while waiting, then displays the catalog including names without descriptions', async () => {
  let finish!: (categories: Awaited<ReturnType<typeof listCategories>>) => void
  load.mockReturnValue(
    new Promise((resolve) => {
      finish = resolve
    }),
  )
  renderWithQuery(<CategoryList />)

  expect(screen.getByRole('status')).toHaveTextContent('Loading categories')
  finish([
    { name: 'HATCHBACK', description: 'Compact car.' },
    { name: 'SUV', description: null },
  ])

  const list = await screen.findByRole('list', { name: 'Vehicle categories' })
  const items = within(list).getAllByRole('listitem')
  expect(items).toHaveLength(2)
  expect(within(items[0]).getByText('HATCHBACK')).toBeVisible()
  expect(within(items[0]).getByText('Compact car.')).toBeVisible()
  expect(items[1]).toHaveTextContent(/^SUV$/)
  expect(screen.queryByRole('status')).not.toBeInTheDocument()
})

it('shows the empty state when there are no categories', async () => {
  load.mockResolvedValue([])
  renderWithQuery(<CategoryList />)

  expect(await screen.findByText('No vehicle categories available.')).toBeInTheDocument()
  expect(screen.queryByRole('list')).not.toBeInTheDocument()
})

it('shows an error and recovers when the user retries', async () => {
  load
    .mockRejectedValueOnce(new Error('Unavailable'))
    .mockResolvedValueOnce([{ name: 'VAN', description: 'Passenger van.' }])
  renderWithQuery(<CategoryList />)

  const alert = await screen.findByRole('alert')
  expect(alert).toHaveTextContent('Could not load vehicle categories.')
  fireEvent.click(within(alert).getByRole('button', { name: 'Try again' }))

  expect(await screen.findByText('Passenger van.')).toBeInTheDocument()
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  expect(load).toHaveBeenCalledTimes(2)
})
