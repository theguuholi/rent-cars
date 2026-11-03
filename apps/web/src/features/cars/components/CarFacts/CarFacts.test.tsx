import { render, screen } from '@testing-library/react'
import { describe, test as scenario, expect } from 'vitest'
import { car } from '../../../../test/car-fixtures'
import { CarFacts } from './CarFacts'

describe('CarFacts', () => {
  scenario.each([
    [true, 'Available'],
    [false, 'Unavailable'],
  ] as const)(
    'Given a car with availability %s, when showing its facts, then its status is %s and charges are displayed in BRL',
    (available, status) => {
      render(<CarFacts car={{ ...car, available }} />)
      for (const [label, value] of [
        ['Brand', 'Toyota'],
        ['License plate', 'ABC1234'],
        ['Availability', status],
        ['Daily rate', 'R$ 150,50'],
        ['Fine amount', 'R$ 5,00'],
      ])
        expect(screen.getByText(label, { selector: 'dt' }).nextElementSibling).toHaveTextContent(
          value,
        )
    },
  )
})
