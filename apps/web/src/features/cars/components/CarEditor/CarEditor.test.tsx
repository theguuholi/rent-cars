import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, test as scenario, vi } from 'vitest'
import { CarEditor } from './CarEditor'
import { car, categories, specifications } from '../../../../test/car-fixtures'

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
      for (const label of ['Daily rate (BRL)', 'Fine amount (BRL)']) {
        expect(screen.getByLabelText(label)).toHaveValue('')
      }
      const submit = new Event('submit', { bubbles: true, cancelable: true })
      fireEvent(screen.getByRole('button', { name: 'Save car' }).closest('form')!, submit)
      expect(submit.defaultPrevented).toBe(true)
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
      for (const label of ['Daily rate (BRL)', 'Fine amount (BRL)']) {
        expect(screen.getByLabelText(label)).toHaveAccessibleDescription(
          'Enter a nonnegative BRL amount with up to two decimal places.',
        )
      }
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
      expect(screen.getByLabelText('License plate')).not.toHaveAttribute('readonly')
      expect(screen.queryByText('No specifications available.')).not.toBeInTheDocument()
      change('License plate', 'ß'.repeat(11))
      await waitFor(() =>
        expect(screen.getByLabelText('License plate')).toHaveAccessibleDescription(
          'Use at most 20 characters.',
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
      const {
        id: _id,
        version: _version,
        category: _category,
        specifications: _specifications,
        ...expected
      } = car
      await waitFor(() => expect(onSave).toHaveBeenCalledExactlyOnceWith(expected))
    },
  )

  scenario(
    'Given an existing car, when specifications are cleared and a save is pending, then the plate remains read-only and duplicate writes are disabled',
    async () => {
      const props = { car, categories, specifications, onSave: vi.fn(), onCancel: vi.fn() }
      const view = render(<CarEditor {...props} busy={false} />)
      expect(screen.getByLabelText('License plate')).toHaveAttribute('readonly')
      expect(screen.getByLabelText('Name')).not.toHaveAttribute('readonly')
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
  scenario(
    'Given padded car attributes, when validating their normalized lengths, then each field accepts its exact registration limit',
    async () => {
      render(
        <CarEditor
          categories={categories}
          specifications={specifications}
          busy={false}
          onSave={vi.fn()}
          onCancel={vi.fn()}
        />,
      )
      for (const [label, value] of [
        ['Name', 'ß'.repeat(100)],
        ['Description', 'd'.repeat(255)],
        ['Brand', 'b'.repeat(80)],
        ['License plate', 'a'.repeat(20)],
      ]) {
        const field = screen.getByLabelText(label)
        fireEvent.change(field, { target: { value: value + 'x' } })
        await waitFor(() =>
          expect(field).toHaveAccessibleDescription(`Use at most ${value.length} characters.`),
        )
        fireEvent.change(field, { target: { value: ` ${value} ` } })
        await waitFor(() => expect(field).toHaveAttribute('aria-invalid', 'false'))
      }
    },
  )

  scenario(
    'Given two selected specifications, when one is removed and the car is saved, then the other remains selected',
    async () => {
      const onSave = vi.fn()
      render(
        <CarEditor
          car={{ ...car, specificationIds: ['gps', 'bluetooth'] }}
          categories={categories}
          specifications={[
            ...specifications,
            { id: 'bluetooth', version: 0, name: 'Bluetooth', description: 'Wireless audio' },
          ]}
          busy={false}
          onSave={onSave}
          onCancel={vi.fn()}
        />,
      )
      fireEvent.click(screen.getByLabelText('GPS'))
      expect(screen.getByLabelText('Bluetooth')).toBeChecked()
      fireEvent.click(screen.getByText('Save car'))
      await waitFor(() =>
        expect(onSave).toHaveBeenCalledWith(
          expect.objectContaining({ specificationIds: ['bluetooth'] }),
        ),
      )
    },
  )
})
