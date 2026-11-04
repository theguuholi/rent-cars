import { render, screen } from '@testing-library/react'
import { describe, expect, test as scenario } from 'vitest'
import { car, specifications } from '../../../../test/car-fixtures'
import { CarCatalogDetails } from './CarCatalogDetails'
describe('CarCatalogDetails', () => {
  scenario.each([
    [specifications, 'GPS'],
    [[], 'None'],
    [[...specifications, { id: '2', name: 'ABS', description: 'Brakes' }], 'GPS, ABS'],
  ] as const)(
    'Given equipment %j, when rendering the car, then the selection reads %s',
    (items, expected) => {
      render(<CarCatalogDetails car={{ category: car.category, specifications: [...items] }} />)
      expect(screen.getByText('Category').nextElementSibling).toHaveTextContent('Sedan')
      expect(screen.getByText('Specifications').nextElementSibling).toHaveTextContent(expected)
    },
  )
})
