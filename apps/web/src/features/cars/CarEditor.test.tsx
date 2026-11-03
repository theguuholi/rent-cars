import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, test as scenario, vi } from 'vitest'
import { CarEditor } from './CarEditor'
import { car, categories, specifications } from '../../test/car-fixtures'

describe('CarEditor', () => {
  scenario(
    'Given a new vehicle, when a blank form is submitted, then each required input explains its error',
    async () => {
      const onSave = vi.fn()
      render(
        <CarEditor
          categories={[]}
          specifications={[]}
          busy={false}
          onSave={onSave}
          onCancel={vi.fn()}
        />,
      )
      expect(screen.getByText('No specifications available.')).toBeVisible()
      expect(screen.getByLabelText('Name')).toHaveFocus()
      expect(screen.getByLabelText('Available')).toBeChecked()
      fireEvent.click(screen.getByText('Save car'))
      await waitFor(() =>
        expect(screen.getByLabelText('Name')).toHaveAccessibleDescription('Enter a name.'),
      )
      for (const label of [
        'Brand',
        'Description',
        'License plate',
        'Category',
        'Daily rate (BRL)',
        'Fine amount (BRL)',
      ])
        expect(screen.getByLabelText(label)).toHaveAttribute('aria-invalid', 'true')
      expect(onSave).not.toHaveBeenCalled()
    },
  )

  scenario(
    'Given a new car, when input is corrected and saved, then the draft uses trimmed fields, uppercase plate, integer cents and selected catalog IDs',
    async () => {
      const onSave = vi.fn()
      render(
        <CarEditor
          categories={categories}
          specifications={specifications}
          busy={false}
          onSave={onSave}
          onCancel={vi.fn()}
        />,
      )
      const change = (label: string, value: string) =>
        fireEvent.change(screen.getByLabelText(label), { target: { value } })
      change('License plate', 'ß'.repeat(128))
      await waitFor(() =>
        expect(screen.getByLabelText('License plate')).toHaveAccessibleDescription(
          'Use at most 255 characters.',
        ),
      )
      change('Daily rate (BRL)', '1,001')
      expect(screen.getByLabelText('Daily rate (BRL)')).toHaveAttribute('aria-invalid', 'true')
      change('License plate', ' abc1234 ')
      change('Name', ' Corolla ')
      change('Brand', ' Toyota ')
      change('Description', ' Family sedan ')
      change('Daily rate (BRL)', '150,5')
      change('Fine amount (BRL)', '5')
      change('Category', 'cat')
      fireEvent.click(screen.getByLabelText('Available'))
      fireEvent.click(screen.getByLabelText('GPS'))
      fireEvent.click(screen.getByText('Save car'))
      const { id: _id, ...expected } = car
      await waitFor(() => expect(onSave).toHaveBeenCalledExactlyOnceWith(expected))
    },
  )

  scenario(
    'Given an existing car, when specifications are cleared and a save is pending, then the plate remains read-only and duplicate writes are disabled',
    async () => {
      const props = { car, categories, specifications, onSave: vi.fn(), onCancel: vi.fn() }
      const view = render(<CarEditor {...props} busy={false} />)
      expect(screen.getByLabelText('License plate')).toHaveAttribute('readonly')
      expect(screen.getByLabelText('Daily rate (BRL)')).toHaveValue('150,50')
      expect(screen.getByLabelText('Fine amount (BRL)')).toHaveValue('5,00')
      expect(screen.getByLabelText('Available')).not.toBeChecked()
      fireEvent.click(screen.getByLabelText('GPS'))
      fireEvent.click(screen.getByText('Save car'))
      await waitFor(() =>
        expect(props.onSave).toHaveBeenCalledWith(
          expect.objectContaining({ specificationIds: [] }),
        ),
      )
      fireEvent.click(screen.getByText('Cancel'))
      expect(props.onCancel).toHaveBeenCalledOnce()
      view.rerender(<CarEditor {...props} busy />)
      expect(screen.getByText('Saving…')).toBeDisabled()
      expect(screen.getByLabelText('Name')).toBeDisabled()
    },
  )
})
