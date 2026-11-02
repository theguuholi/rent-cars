import { render, screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import App from './App'

it('shows the Rent Cars heading', () => {
  render(<App />)

  expect(
    screen.getByRole('heading', { level: 1, name: 'Rent Cars' }),
  ).toBeInTheDocument()
})