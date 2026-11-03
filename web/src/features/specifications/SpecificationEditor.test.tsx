import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import { SpecificationEditor } from './SpecificationEditor'

it('accepts a normalized name of exactly 255 characters and submits trimmed fields', async () => {
  const onSave = vi.fn()
  render(<SpecificationEditor busy={false} onSave={onSave} onCancel={vi.fn()} />)
  const name = 'ß'.repeat(127) + 'a'
  fireEvent.change(screen.getByLabelText('Name'), { target: { value: ' ' + name + ' ' } })
  fireEvent.change(screen.getByLabelText('Description'), { target: { value: '  Braking  ' } })
  fireEvent.click(screen.getByRole('button', { name: 'Save specification' }))
  await waitFor(() =>
    expect(onSave).toHaveBeenCalledExactlyOnceWith({ name, description: 'Braking' }),
  )
})

it('associates validation messages with each invalid field and allows cancelling the draft', async () => {
  const onSave = vi.fn(),
    onCancel = vi.fn()
  render(<SpecificationEditor busy={false} onSave={onSave} onCancel={onCancel} />)
  fireEvent.click(screen.getByRole('button', { name: 'Save specification' }))
  await waitFor(() =>
    expect(screen.getByLabelText('Name')).toHaveAccessibleDescription('Enter a name.'),
  )
  expect(screen.getByLabelText('Description')).toHaveAccessibleDescription('Enter a description.')
  fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))
  expect(onCancel).toHaveBeenCalledOnce()
  expect(onSave).not.toHaveBeenCalled()
})
