import { render, screen } from '@testing-library/react'
import { describe, expect, test as scenario } from 'vitest'
import { AccountPanel } from './AccountPanel'

describe('AccountPanel', () => {
  scenario(
    'Given an account form, when displayed in its panel, then its controls remain accessible',
    () => {
      render(
        <AccountPanel>
          <label>
            Email
            <input type="email" />
          </label>
          <button>Sign in</button>
        </AccountPanel>,
      )
      expect(screen.getByRole('textbox', { name: 'Email' })).toBeVisible()
      expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled()
    },
  )
})
