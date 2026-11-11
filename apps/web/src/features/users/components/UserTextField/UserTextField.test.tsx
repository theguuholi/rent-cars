import { fireEvent, render, screen } from '@testing-library/react'
import { expect, test, vi } from 'vitest'
import { UserTextField } from './UserTextField'

test('Given a field error, when entering a value, then the labelled input reports the error and change', () => {
  const onChange = vi.fn()
  render(
    <UserTextField
      label="First name"
      error="Required"
      input={{ value: 'Ada', onChange, required: true }}
    />,
  )
  const input = screen.getByRole('textbox', { name: 'First name' })
  expect(input).toHaveAttribute('aria-invalid', 'true')
  expect(input).toHaveAttribute('aria-describedby', screen.getByRole('alert').id)
  expect(screen.getByRole('alert')).toHaveTextContent('Required')
  fireEvent.change(input, { target: { value: 'Grace' } })
  expect(onChange).toHaveBeenCalledOnce()
})
