import { render, screen, within } from '@testing-library/react'
import { describe, expect, test as scenario } from 'vitest'
import { CatalogTable } from './CatalogTable'

describe('CatalogTable', () => {
  scenario(
    'Given a named catalog, when rendered, then headers, selected rows and actions retain their semantics',
    () => {
      render(
        <CatalogTable aria-label="Equipment">
          <thead>
            <tr>
              <th>Name</th>
            </tr>
          </thead>
          <tbody>
            <tr data-selected="true">
              <td>
                GPS<button>Edit</button>
              </td>
            </tr>
          </tbody>
        </CatalogTable>,
      )
      const table = within(screen.getByRole('table', { name: 'Equipment' }))
      expect(table.getByRole('columnheader', { name: 'Name' })).toBeVisible()
      expect(table.getByRole('row', { name: 'GPS Edit' })).toHaveAttribute('data-selected', 'true')
      expect(table.getByRole('button', { name: 'Edit' })).toBeEnabled()
    },
  )
})
