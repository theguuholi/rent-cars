import { render, screen, within } from '@testing-library/react'
import { describe, expect, test as scenario } from 'vitest'
import { FormSection } from './FormSection'

describe('FormSection', () => {
  scenario(
    'Given related fields, when grouped, then their legend names the group and labels remain available',
    () => {
      render(
        <FormSection title="Pricing">
          <label>
            Daily rate
            <input />
          </label>
        </FormSection>,
      )
      expect(
        within(screen.getByRole('group', { name: 'Pricing' })).getByRole('textbox', {
          name: 'Daily rate',
        }),
      ).toBeVisible()
    },
  )
})
