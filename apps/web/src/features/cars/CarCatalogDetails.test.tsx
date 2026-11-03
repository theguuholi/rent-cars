import { render, screen } from '@testing-library/react'
import { describe, test as scenario, expect } from 'vitest'
import { car, categories, specifications } from '../../test/car-fixtures'
import { CarCatalogDetails } from './CarCatalogDetails'

describe('CarCatalogDetails', () => {
  scenario(
    'Given known and missing equipment IDs, when rendering the selection, then the category and equipment names appear in selection order with missing IDs preserved',
    () => {
      render(
        <CarCatalogDetails
          car={{ categoryId: car.categoryId, specificationIds: ['air', 'unknown', 'gps'] }}
          categories={[{ id: 'van', name: 'Van', description: null }, ...categories]}
          specifications={[
            ...specifications,
            { id: 'air', name: 'Air conditioning', description: 'Cooling' },
          ]}
        />,
      )
      expect(screen.getByText('Category', { selector: 'dt' }).nextElementSibling).toHaveTextContent(
        'Sedan',
      )
      expect(
        screen.getByText('Specifications', { selector: 'dt' }).nextElementSibling,
      ).toHaveTextContent('Air conditioning, unknown, GPS')
    },
  )
  scenario(
    'Given an unknown category and no assigned equipment, when opening details, then the category ID and the empty selection are shown',
    () => {
      render(
        <CarCatalogDetails
          car={{ categoryId: 'missing', specificationIds: [] }}
          categories={categories}
          specifications={specifications}
        />,
      )
      expect(screen.getByText('Category', { selector: 'dt' }).nextElementSibling).toHaveTextContent(
        'missing',
      )
      expect(
        screen.getByText('Specifications', { selector: 'dt' }).nextElementSibling,
      ).toHaveTextContent('None')
    },
  )
})
