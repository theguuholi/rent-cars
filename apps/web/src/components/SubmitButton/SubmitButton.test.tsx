import { useForm } from '@tanstack/react-form'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, test as scenario, vi } from 'vitest'
import { SubmitButton } from './SubmitButton'

function Example({ save }: { save: () => Promise<void> }) {
  const form = useForm({ defaultValues: {}, onSubmit: save })
  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        void form.handleSubmit()
      }}
    >
      <SubmitButton form={form}>Save changes</SubmitButton>
    </form>
  )
}

describe('SubmitButton', () => {
  scenario(
    'Given an async submission, when saving and finishing, then prevent duplicate clicks and re-enable submission',
    async () => {
      let finish!: () => void
      const pending = new Promise<void>((resolve) => {
        finish = resolve
      })
      const save = vi.fn(() => pending)
      render(<Example save={save} />)
      const button = screen.getByRole('button', { name: 'Save changes' })
      expect(button).toHaveAttribute('type', 'submit')
      expect(button).toHaveAttribute('data-variant', 'primary')
      expect(button).toBeEnabled()
      fireEvent.click(button)
      await waitFor(() => expect(button).toBeDisabled())
      fireEvent.click(button)
      expect(save).toHaveBeenCalledTimes(1)
      await act(async () => finish())
      expect(button).toBeEnabled()
    },
  )
})
