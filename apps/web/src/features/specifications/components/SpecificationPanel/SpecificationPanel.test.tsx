import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, test as scenario, vi } from 'vitest'
import { SpecificationPanel } from './SpecificationPanel'

const item = { id: 'gps-id', name: 'GPS', description: 'Navigation' }
const callbacks = () => ({ onSave: vi.fn(), onCancel: vi.fn(), onDelete: vi.fn() })

describe('SpecificationPanel', () => {
  scenario(
    'Given a new specification, when its panel opens, then it focuses the empty editor and lets the user cancel',
    () => {
      const actions = callbacks()
      render(
        <SpecificationPanel {...actions} busy={false} deleting={false} catalogLoaded={false} />,
      )
      const panel = within(screen.getByRole('complementary', { name: 'Specification details' }))
      expect(panel.getByRole('group', { name: 'New specification' })).toBeVisible()
      expect(panel.getByLabelText('Name')).toHaveFocus()
      fireEvent.click(panel.getByRole('button', { name: 'Cancel' }))
      expect(actions.onCancel).toHaveBeenCalledOnce()
      expect(actions.onSave).not.toHaveBeenCalled()
    },
  )

  scenario(
    'Given a record selected for deletion, when the confirmation opens, then Cancel has focus and only an explicit confirmation deletes its ID',
    () => {
      const actions = callbacks()
      render(
        <SpecificationPanel
          {...actions}
          id={item.id}
          item={item}
          deleting
          catalogLoaded
          busy={false}
        />,
      )
      expect(screen.getByRole('heading', { name: 'Delete GPS?' })).toBeVisible()
      expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus()
      expect(actions.onDelete).not.toHaveBeenCalled()
      fireEvent.click(screen.getByRole('button', { name: 'Confirm deletion' }))
      expect(actions.onDelete).toHaveBeenCalledExactlyOnceWith(item.id)
      expect(actions.onCancel).not.toHaveBeenCalled()
    },
  )
})
