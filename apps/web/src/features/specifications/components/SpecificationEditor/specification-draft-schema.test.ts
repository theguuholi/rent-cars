import { describe, expect, test as scenario } from 'vitest'
import { specificationDraftSchema, specificationFieldError } from './specification-draft-schema'

describe('specificationDraftSchema', () => {
  scenario('Given padded fields, when parsed, then produce a trimmed input', () => {
    expect(specificationDraftSchema.parse({ name: ' GPS ', description: ' Maps ' })).toEqual({
      name: 'GPS',
      description: 'Maps',
    })
  })

  scenario.each([
    ['name', ' ', 'Enter a name.'],
    ['name', 'ß'.repeat(128), 'Use at most 255 characters after uppercase conversion.'],
    ['description', ' ', 'Enter a description.'],
  ] as const)('Given invalid %s, when checked, then return its error', (field, value, message) => {
    expect(specificationFieldError(field, value)).toBe(message)
    expect(
      specificationDraftSchema.safeParse({ name: 'GPS', description: 'Maps', [field]: value })
        .success,
    ).toBe(false)
  })

  scenario('Given 255 uppercase characters, when checked, then accept the name', () => {
    expect(specificationFieldError('name', ` ${'ß'.repeat(127)}a `)).toBeUndefined()
    expect(specificationFieldError('description', ' Maps ')).toBeUndefined()
  })
})
