import { describe, expect, test as scenario } from 'vitest'
import { carDraftSchema, carFieldError } from './car-draft-schema'

const valid = {
  name: ' Corolla ',
  description: ' Family sedan ',
  brand: ' Toyota ',
  licensePlate: ' abc1234 ',
  available: true,
  categoryId: 'cat',
  specificationIds: ['gps'],
  dailyRate: '150,5',
  fineAmount: '5',
}

describe('carDraftSchema', () => {
  scenario('Given a valid draft, when parsed, then produce the API input', () => {
    expect(carDraftSchema.parse(valid)).toEqual({
      name: 'Corolla',
      description: 'Family sedan',
      brand: 'Toyota',
      licensePlate: 'ABC1234',
      available: true,
      categoryId: 'cat',
      specificationIds: ['gps'],
      dailyRateCents: 15050,
      fineAmountCents: 500,
    })
  })

  scenario.each([
    ['name', ' ', 'Enter a name.'],
    ['name', 'a'.repeat(101), 'Use at most 100 characters.'],
    ['description', ' ', 'Enter a description.'],
    ['description', 'a'.repeat(256), 'Use at most 255 characters.'],
    ['brand', ' ', 'Enter a brand.'],
    ['brand', 'a'.repeat(81), 'Use at most 80 characters.'],
    ['licensePlate', ' ', 'Enter a license plate.'],
    ['licensePlate', 'ß'.repeat(11), 'Use at most 20 characters.'],
    ['dailyRate', '1,001', 'Enter a nonnegative BRL amount with up to two decimal places.'],
    ['fineAmount', '-1', 'Enter a nonnegative BRL amount with up to two decimal places.'],
    ['categoryId', '', 'Select a category.'],
  ] as const)('Given invalid %s, when checked, then return %s', (field, value, message) => {
    expect(carFieldError(field, value)).toBe(message)
    expect(carDraftSchema.safeParse({ ...valid, [field]: value }).success).toBe(false)
  })

  scenario('Given padded values at their limits, when checked, then accept them', () => {
    for (const [field, value] of [
      ['name', ` ${'a'.repeat(100)} `],
      ['description', ` ${'a'.repeat(255)} `],
      ['brand', ` ${'a'.repeat(80)} `],
      ['licensePlate', ` ${'a'.repeat(20)} `],
      ['dailyRate', '0'],
      ['fineAmount', '0'],
      ['categoryId', 'cat'],
    ] as const) {
      expect(carFieldError(field, value)).toBeUndefined()
    }
  })
})
