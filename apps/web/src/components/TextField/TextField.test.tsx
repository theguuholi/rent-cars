import { useForm } from '@tanstack/react-form'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, expectTypeOf, test as scenario, vi } from 'vitest'
import { TextField } from './TextField'

function Example({ onSave = vi.fn() }) {
  const form = useForm({
    defaultValues: { lastName: 'Lovelace', email: '', enabled: true },
    onSubmit: ({ value }) => onSave(value),
  })
  expectTypeOf<Parameters<typeof TextField<typeof form>>[0]['name']>().toEqualTypeOf<
    'lastName' | 'email'
  >()
  // @ts-expect-error Boolean values are not supported by text inputs.
  const invalidType = <TextField form={form} name="enabled" />
  // @ts-expect-error The name must exist in the supplied form.
  const missingName = <TextField form={form} name="missing" />
  void invalidType
  void missingName
  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        void form.handleSubmit()
      }}
    >
      <TextField form={form} name="lastName" required autoComplete="family-name" />
      <TextField
        form={form}
        name="email"
        label="Contact email"
        type="email"
        maxLength={254}
        aria-describedby="email-hint"
      />
      <p id="email-hint">Use your work email.</p>
      <button
        type="button"
        onClick={() => form.setErrorMap({ onSubmit: { fields: { email: 'Already registered' } } })}
      >
        Server error
      </button>
      <button type="button" onClick={() => form.setErrorMap({ onSubmit: { fields: {} } })}>
        Clear errors
      </button>
      <form.Subscribe selector={(state) => state.fieldMeta.lastName?.isTouched}>
        {(touched) => <output>{String(touched)}</output>}
      </form.Subscribe>
      <button type="submit">Save</button>
    </form>
  )
}

describe('TextField', () => {
  scenario(
    'Given a camelCase name, when rendered and edited, then label, default limit, blur and submitted value are connected',
    async () => {
      const onSave = vi.fn()
      render(<Example onSave={onSave} />)
      const input = screen.getByRole('textbox', { name: 'Last name' })
      expect(input).toHaveValue('Lovelace')
      expect(input).toHaveAttribute('name', 'lastName')
      expect(input).toHaveAttribute('type', 'text')
      expect(input).toHaveAttribute('maxlength', '80')
      expect(input).toHaveAttribute('autocomplete', 'family-name')
      expect(input).toHaveClass('control')
      expect(input).toBeRequired()
      expect(input).toHaveAttribute('aria-invalid', 'false')
      expect(input).not.toHaveAttribute('aria-describedby')
      expect(screen.getByRole('status')).toHaveTextContent('false')
      fireEvent.change(input, { target: { value: 'Hopper' } })
      fireEvent.blur(input)
      expect(screen.getByRole('status')).toHaveTextContent('true')
      fireEvent.click(screen.getByRole('button', { name: 'Save' }))
      await waitFor(() =>
        expect(onSave).toHaveBeenCalledWith({ lastName: 'Hopper', email: '', enabled: true }),
      )
    },
  )

  scenario(
    'Given an explicit label and hint, when a form error arrives and clears, then accessible descriptions preserve the hint and current value',
    () => {
      render(<Example />)
      const input = screen.getByRole('textbox', { name: 'Contact email' })
      expect(input).toHaveAttribute('type', 'email')
      expect(input).toHaveAttribute('maxlength', '254')
      expect(input).toHaveAccessibleDescription('Use your work email.')
      fireEvent.change(input, { target: { value: 'ada@example.test' } })
      fireEvent.click(screen.getByRole('button', { name: 'Server error' }))
      expect(input).toHaveAttribute('aria-invalid', 'true')
      expect(screen.getByRole('alert')).toHaveTextContent('Already registered')
      expect(input).toHaveAccessibleDescription('Use your work email. Already registered')
      fireEvent.click(screen.getByRole('button', { name: 'Clear errors' }))
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
      expect(input).toHaveAttribute('aria-invalid', 'false')
      expect(input).toHaveAccessibleDescription('Use your work email.')
      expect(input).toHaveValue('ada@example.test')
    },
  )
})

function SchemaExample() {
  const form = useForm({ defaultValues: { email: '', firstName: '' } })
  return (
    <>
      <TextField
        form={form}
        name="email"
        id="contact-email"
        className="custom-control"
        placeholder="you@example.test"
        readOnly
      />
      <TextField form={form} name="firstName" disabled />
      <button
        onClick={() =>
          form.setErrorMap({
            onSubmit: {
              fields: {
                email: [{ message: 'Email is invalid' }, { message: 'Use a company address' }],
              },
            },
          })
        }
      >
        Schema errors
      </button>
    </>
  )
}

describe('TextField accessibility', () => {
  scenario(
    'Given schema issues and a custom ID, when errors arrive, then all messages describe the correct input without a hint',
    () => {
      render(<SchemaExample />)
      const input = screen.getByRole('textbox', { name: 'Email' })
      expect(input).toHaveAttribute('id', 'contact-email')
      expect(input).toHaveAttribute('placeholder', 'you@example.test')
      expect(input).toHaveAttribute('readonly')
      expect(input).toHaveClass('custom-control')
      expect(screen.getByRole('textbox', { name: 'First name' })).toBeDisabled()
      expect(screen.getByRole('textbox', { name: 'First name' })).not.toHaveAttribute(
        'aria-describedby',
      )
      fireEvent.click(screen.getByRole('button', { name: 'Schema errors' }))
      expect(input).toHaveAccessibleDescription('Email is invalid Use a company address')
      expect(input).toHaveAttribute('aria-describedby', 'contact-email-error')
      expect(screen.getByRole('alert')).toHaveAttribute('id', 'contact-email-error')
      expect(screen.getByRole('textbox', { name: 'First name' })).toHaveAttribute(
        'aria-invalid',
        'false',
      )
    },
  )

  scenario(
    'Given separate forms with the same field name, when rendered, then each label targets a unique input',
    () => {
      render(
        <>
          <Example />
          <Example />
        </>,
      )
      const [first, second] = screen.getAllByRole('textbox', { name: 'Last name' })
      expect(first.id).not.toBe(second.id)
      expect(first.id).not.toBe('')
      expect(second.id).not.toBe('')
    },
  )
})
