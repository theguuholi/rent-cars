import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, test as scenario, vi } from 'vitest'
import { Button } from './Button'

describe('Button', () => {
  scenario(
    'Given an action, when clicked, then it runs without implicitly submitting its form',
    () => {
      const click = vi.fn()
      const submit = vi.fn()
      render(
        <form onSubmit={submit}>
          <Button onClick={click}>Continue</Button>
        </form>,
      )
      const button = screen.getByRole('button', { name: 'Continue' })
      fireEvent.click(button)
      expect(click).toHaveBeenCalledOnce()
      expect(submit).not.toHaveBeenCalled()
      expect(button).toHaveAttribute('data-variant', 'primary')
    },
  )
  scenario(
    'Given a disabled destructive submit action, when clicked, then it stays disabled and does not run',
    () => {
      const click = vi.fn()
      render(
        <Button variant="danger" type="submit" disabled onClick={click}>
          Delete
        </Button>,
      )
      const button = screen.getByRole('button', { name: 'Delete' })
      fireEvent.click(button)
      expect(button).toBeDisabled()
      expect(button).toHaveAttribute('type', 'submit')
      expect(button).toHaveAttribute('data-variant', 'danger')
      expect(click).not.toHaveBeenCalled()
    },
  )
  scenario(
    'Given a secondary action, when rendered, then its alternative emphasis is explicit',
    () => {
      render(<Button variant="secondary">Cancel</Button>)
      expect(screen.getByRole('button', { name: 'Cancel' })).toHaveAttribute(
        'data-variant',
        'secondary',
      )
    },
  )
})
