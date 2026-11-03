import { describe, expect, test as scenario } from 'vitest'
import { car } from '../../test/car-fixtures'
import { changedCarFields } from './changed-car-fields'

describe('Fields included in a car PATCH', () => {
  scenario(
    'Given padded stored text and two specifications, when the form submits the same normalized values in a different selection order, then no fields are changed',
    () => {
      const original = {
        ...car,
        name: ' Corolla ',
        description: ' Family sedan ',
        brand: ' Toyota ',
        specificationIds: ['gps', 'air'],
      }
      const submitted = { ...car, specificationIds: ['air', 'gps'] }
      expect(changedCarFields(original, submitted)).toEqual({})
    },
  )

  scenario(
    'Given an existing car, when all editable values change, then the patch includes the new values, including false, zero and an empty equipment selection',
    () => {
      const original = { ...car, available: true }
      const changes = {
        name: 'Civic',
        description: 'Updated sedan',
        brand: 'Honda',
        dailyRateCents: 0,
        fineAmountCents: 0,
        categoryId: 'other-category',
        available: false,
        specificationIds: [],
      }
      expect(
        changedCarFields(original, { ...original, ...changes, licensePlate: 'OTHER' }),
      ).toEqual(changes)
    },
  )

  scenario(
    'Given existing equipment, when one item is replaced without changing the number selected, then only the replacement selection is sent',
    () => {
      const original = { ...car, specificationIds: ['gps', 'air'] }
      const specificationIds = ['gps', 'bluetooth']
      expect(changedCarFields(original, { ...original, specificationIds })).toEqual({
        specificationIds,
      })
    },
  )
})
