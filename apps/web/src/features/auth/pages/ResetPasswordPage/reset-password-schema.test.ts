import { describe, expect, test as scenario } from 'vitest'
import { parseResetPassword } from './reset-password-schema'

describe('resetPasswordSchema', () => {
  scenario('Given matching valid passwords, when parsed, then return the submitted values', () => {
    const input = {
      password: 'replacement-password-123',
      passwordConfirmation: 'replacement-password-123',
    }
    expect(parseResetPassword(input)).toEqual({ data: input, errors: {} })
  })

  scenario.each([
    [
      { password: 'short', passwordConfirmation: 'short' },
      'password',
      'Choose a nonblank password.',
    ],
    [
      { password: ' '.repeat(12), passwordConfirmation: ' '.repeat(12) },
      'password',
      'Choose a nonblank password.',
    ],
    [
      { password: 'é'.repeat(37), passwordConfirmation: 'é'.repeat(37) },
      'password',
      'Choose a nonblank password.',
    ],
    [
      { password: 'replacement-password-123', passwordConfirmation: '' },
      'passwordConfirmation',
      'Confirm the new password.',
    ],
    [
      { password: 'replacement-password-123', passwordConfirmation: 'different-password-123' },
      'passwordConfirmation',
      'Passwords do not match.',
    ],
  ] as const)('Given invalid input, when parsed, then report %s', (input, field, message) => {
    expect(parseResetPassword(input)).toMatchObject({
      data: null,
      errors: { [field]: expect.stringContaining(message) },
    })
  })
})
