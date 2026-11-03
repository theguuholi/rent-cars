import { fireEvent, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, test as scenario, vi } from 'vitest'
import { renderWithQuery } from '../../../../test/render-with-query'
import { CategoriesPage } from './CategoriesPage'
import { listCategories } from '../../categories-api'

vi.mock('../../categories-api', () => ({ listCategories: vi.fn() }))

describe('CategoriesPage', () => {
  const load = vi.mocked(listCategories)
  beforeEach(() => {
    load.mockReset()
  })

  function openPage() {
    renderWithQuery(<CategoriesPage />)
    return within(screen.getByRole('region', { name: 'Vehicle categories' }))
  }

  scenario(
    'Given the categories page, when it opens, then the Catalog label and level-two heading identify the region',
    () => {
      load.mockReturnValue(new Promise(() => {}))
      const page = openPage()
      expect(page.getByText('Catalog')).toBeVisible()
      const title = page.getByRole('heading', { level: 2, name: 'Vehicle categories' })
      expect(title).toBeVisible()
      expect(screen.getByRole('region')).toHaveAttribute('aria-labelledby', title.id)
    },
  )

  scenario(
    'Given a pending catalog request, when the page opens, then the region shows loading without an empty state or list',
    () => {
      load.mockReturnValue(new Promise(() => {}))
      const page = openPage()
      expect(page.getByRole('status')).toHaveTextContent(/^Loading categories…$/)
      expect(page.queryByRole('list')).not.toBeInTheDocument()
      expect(page.queryByText('No vehicle categories available.')).not.toBeInTheDocument()
    },
  )

  scenario(
    'Given categories with and without descriptions, when loading succeeds, then each row shows its own details in catalog order',
    async () => {
      load.mockResolvedValue([
        { id: 'coupe', name: 'COUPE', description: 'Two doors' },
        { id: 'suv', name: 'SUV', description: null },
      ])
      const page = openPage()
      const catalog = within(await page.findByRole('list', { name: 'Vehicle categories' }))
      const rows = catalog.getAllByRole('listitem')
      expect(rows).toHaveLength(2)
      expect(within(rows[0]).getByText('COUPE')).toBeVisible()
      expect(within(rows[0]).getByText('Two doors')).toBeVisible()
      expect(rows[1]).toHaveTextContent(/^SUV$/)
      expect(page.queryByRole('status')).not.toBeInTheDocument()
    },
  )

  scenario(
    'Given an empty catalog, when loading succeeds, then the region displays its empty message without a list or retry action',
    async () => {
      load.mockResolvedValue([])
      const page = openPage()
      expect(await page.findByText('No vehicle categories available.')).toBeVisible()
      expect(page.queryByRole('list')).not.toBeInTheDocument()
      expect(page.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
    },
  )

  scenario(
    'Given an unavailable API, when loading fails, then the region keeps its heading and offers a retry inside the error alert',
    async () => {
      load.mockRejectedValue(new Error('Offline'))
      const page = openPage()
      const alert = await page.findByRole('alert')
      expect(alert).toHaveTextContent('Could not load vehicle categories.')
      expect(within(alert).getByRole('button', { name: 'Try again' })).toBeEnabled()
      expect(page.getByRole('heading', { name: 'Vehicle categories' })).toBeVisible()
      expect(page.queryByRole('list')).not.toBeInTheDocument()
    },
  )

  scenario(
    'Given a failed first request, when the user retries successfully, then the same region replaces the alert with the returned catalog',
    async () => {
      load
        .mockRejectedValueOnce(new Error('Offline'))
        .mockResolvedValueOnce([{ id: 'van', name: 'VAN', description: 'Passenger van' }])
      const page = openPage()
      fireEvent.click(await page.findByRole('button', { name: 'Try again' }))
      expect(await page.findByRole('listitem')).toHaveTextContent('Passenger van')
      expect(page.queryByRole('alert')).not.toBeInTheDocument()
      expect(load).toHaveBeenCalledTimes(2)
    },
  )
})
