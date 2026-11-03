import { createEvent, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { expect, describe, test as scenario, vi } from 'vitest'
import { SpecificationEditor } from './SpecificationEditor'

describe('SpecificationEditor', () => {
  scenario(
    'Given a name with 255 uppercase characters, when the editor submits, then it prevents native navigation and saves the trimmed fields',
    async () => {
      const onSave = vi.fn()
      render(<SpecificationEditor busy={false} onSave={onSave} onCancel={vi.fn()} />)
      const name = 'ß'.repeat(127) + 'a'
      fireEvent.change(screen.getByLabelText('Name'), { target: { value: ' ' + name + ' ' } })
      fireEvent.change(screen.getByLabelText('Description'), { target: { value: '  Braking  ' } })
      const form = screen.getByRole<HTMLButtonElement>('button', {
        name: 'Save specification',
      }).form!
      const submit = createEvent.submit(form)
      fireEvent(form, submit)
      expect(submit.defaultPrevented).toBe(true)
      await waitFor(() =>
        expect(onSave).toHaveBeenCalledExactlyOnceWith({ name, description: 'Braking' }),
      )
    },
  )

  scenario(
    'Given an empty editor, when the user submits, then each invalid field has an accessible error',
    async () => {
      const onSave = vi.fn(),
        onCancel = vi.fn()
      render(<SpecificationEditor busy={false} onSave={onSave} onCancel={onCancel} />)
      fireEvent.click(screen.getByRole('button', { name: 'Save specification' }))
      await waitFor(() =>
        expect(screen.getByLabelText('Name')).toHaveAccessibleDescription('Enter a name.'),
      )
      expect(screen.getByLabelText('Description')).toHaveAccessibleDescription(
        'Enter a description.',
      )
      fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))
      expect(onCancel).toHaveBeenCalledOnce()
      expect(onSave).not.toHaveBeenCalled()
    },
  )

  describe('Editor modes', () => {
    scenario(
      'Given no existing record, when the editor opens, then both required fields are empty and Name has focus',
      () => {
        render(<SpecificationEditor busy={false} onSave={vi.fn()} onCancel={vi.fn()} />)
        expect(screen.getByRole('group', { name: 'New specification' })).toBeVisible()
        const name = screen.getByLabelText('Name')
        expect(name).toHaveFocus()
        expect(name).toHaveValue('')
        expect(name).toBeRequired()
        expect(screen.getByLabelText('Description')).toHaveValue('')
        expect(screen.getByLabelText('Description')).toBeRequired()
        expect(screen.queryByRole('alert')).not.toBeInTheDocument()
      },
    )

    scenario(
      'Given an existing record, when only its description is edited and saved, then the original name is preserved',
      async () => {
        const onSave = vi.fn()
        const item = { id: 'gps-id', name: 'GPS', description: 'Maps' }
        render(<SpecificationEditor item={item} busy={false} onSave={onSave} onCancel={vi.fn()} />)
        expect(screen.getByRole('group', { name: 'Edit specification' })).toBeVisible()
        expect(screen.getByLabelText('Name')).toHaveValue('GPS')
        expect(screen.getByLabelText('Description')).toHaveValue('Maps')
        fireEvent.change(screen.getByLabelText('Description'), {
          target: { value: ' Updated maps ' },
        })
        fireEvent.click(screen.getByRole('button', { name: 'Save specification' }))
        await waitFor(() =>
          expect(onSave).toHaveBeenCalledExactlyOnceWith({
            name: 'GPS',
            description: 'Updated maps',
          }),
        )
      },
    )

    scenario(
      'Given a save in progress, when the editor is busy, then neither field nor action is available until it finishes',
      () => {
        const props = { onSave: vi.fn(), onCancel: vi.fn() }
        const view = render(<SpecificationEditor {...props} busy />)
        for (const field of screen.getAllByRole('textbox')) expect(field).toBeDisabled()
        expect(screen.getByRole('button', { name: 'Saving…' })).toBeDisabled()
        expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
        view.rerender(<SpecificationEditor {...props} busy={false} />)
        for (const field of screen.getAllByRole('textbox')) expect(field).toBeEnabled()
        expect(screen.getByRole('button', { name: 'Save specification' })).toBeEnabled()
        expect(screen.getByRole('button', { name: 'Cancel' })).toBeEnabled()
      },
    )
  })

  scenario.each([
    ['Name', '   ', 'GPS', 'Enter a name.'],
    ['Name', 'ß'.repeat(128), 'GPS', 'Use at most 255 characters after uppercase conversion.'],
    ['Description', '   ', 'Navigation', 'Enter a description.'],
  ])(
    'Given invalid %s input, when it is corrected, then its linked error clears and the valid draft can be saved',
    async (label, invalid, corrected, message) => {
      const onSave = vi.fn()
      render(
        <SpecificationEditor
          item={{ id: 'gps-id', name: 'GPS', description: 'Navigation' }}
          busy={false}
          onSave={onSave}
          onCancel={vi.fn()}
        />,
      )
      const field = screen.getByLabelText(label)
      fireEvent.change(field, { target: { value: invalid } })
      fireEvent.click(screen.getByRole('button', { name: 'Save specification' }))
      await waitFor(() => expect(field).toHaveAccessibleDescription(message))
      expect(field).toHaveAttribute('aria-invalid', 'true')
      expect(onSave).not.toHaveBeenCalled()
      fireEvent.change(field, { target: { value: corrected } })
      await waitFor(() => expect(field).toHaveAttribute('aria-invalid', 'false'))
      expect(field).not.toHaveAttribute('aria-describedby')
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
      fireEvent.click(screen.getByRole('button', { name: 'Save specification' }))
      await waitFor(() =>
        expect(onSave).toHaveBeenCalledExactlyOnceWith({ name: 'GPS', description: 'Navigation' }),
      )
    },
  )
})
