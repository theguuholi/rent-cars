import { fireEvent, screen, within } from '@testing-library/react'
import { beforeEach, expect, describe, test as scenario, vi } from 'vitest'
import { renderWithQuery } from '../../test/render-with-query'
import { listCategories } from './categories-api'
import { CategoryList } from './CategoryList'

vi.mock('./categories-api', () => ({ listCategories: vi.fn() }))

const load = vi.mocked(listCategories)
describe('CategoryList', () => {
  beforeEach(() => {
    load.mockReset()
  })

  describe('Catalog states', () => {
    scenario(
      'Given CategoryList, when listCategories is still pending, then the status reads “Loading categories…”',
      () => {
        load.mockReturnValue(new Promise(() => {}))
        renderWithQuery(<CategoryList />)

        expect(screen.getByRole('status')).toHaveTextContent(/^Loading categories…$/)
        expect(screen.queryByRole('list')).not.toBeInTheDocument()
      },
    )
    scenario(
      'Given two categories, when listCategories resolves, then the list displays both records in response order',
      async () => {
        load.mockResolvedValue([
          { name: 'HATCHBACK', description: 'Compact car.' },
          { name: 'SUV', description: 'Sport utility vehicle.' },
        ])
        renderWithQuery(<CategoryList />)

        const list = await screen.findByRole('list', { name: 'Vehicle categories' })
        const items = within(list).getAllByRole('listitem')
        expect(items).toHaveLength(2)
        expect(within(items[0]).getByText('HATCHBACK')).toBeVisible()
        expect(within(items[0]).getByText('Compact car.')).toBeVisible()
        expect(within(items[1]).getByText('SUV')).toBeVisible()
        expect(screen.queryByRole('status')).not.toBeInTheDocument()
      },
    )
    scenario(
      'Given a category with a null description, when its row renders, then only its name is shown',
      async () => {
        load.mockResolvedValue([{ name: 'SUV', description: null }])
        renderWithQuery(<CategoryList />)

        expect(await screen.findByRole('listitem')).toHaveTextContent(/^SUV$/)
      },
    )
    scenario(
      'Given an empty catalog, when listCategories resolves, then the empty message replaces the list',
      async () => {
        load.mockResolvedValue([])
        renderWithQuery(<CategoryList />)

        expect(await screen.findByText('No vehicle categories available.')).toBeVisible()
        expect(screen.queryByRole('list')).not.toBeInTheDocument()
      },
    )
  })

  describe('Request failure and retry', () => {
    scenario(
      'Given CategoryList, when listCategories rejects, then the error alert offers a retry',
      async () => {
        load.mockRejectedValue(new Error('Unavailable'))
        renderWithQuery(<CategoryList />)

        const alert = await screen.findByRole('alert')
        expect(alert).toHaveTextContent('Could not load vehicle categories.')
        expect(within(alert).getByRole('button', { name: 'Try again' })).toBeEnabled()
      },
    )
    scenario(
      'Given a failed category request, when the user retries successfully, then the list replaces the alert',
      async () => {
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
      },
    )
  })
})
