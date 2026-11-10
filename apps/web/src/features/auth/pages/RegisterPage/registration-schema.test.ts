import { describe, expect, test as scenario } from 'vitest'
import { registrationErrors, registrationSchema } from './registration-schema'

const valid = {
  firstName: ' Test ',
  lastName: ' Driver ',
  userName: ' Test.Driver ',
  driveLicense: ' 01234567890 ',
  email: 'driver@example.test',
  password: 'test-password-123',
  passwordConfirmation: 'test-password-123',
}

describe('registrationSchema', () => {
  scenario('Given valid values, when parsed, then normalize names and identifiers', () => {
    expect(registrationSchema.parse(valid)).toMatchObject({
      firstName: 'Test',
      lastName: 'Driver',
      userName: 'test.driver',
      driveLicense: '01234567890',
    })
    expect(registrationErrors(valid)).toEqual({})
  })

  scenario('Given multiple invalid fields, when checked, then return each field error', () => {
    expect(
      registrationErrors({
        ...valid,
        firstName: ' ',
        lastName: ' ',
        userName: '@',
        driveLicense: 'invalid',
        email: 'bad',
        password: 'short',
        passwordConfirmation: 'different',
      }),
    ).toEqual({
      firstName: 'Enter a first name of up to 80 characters.',
      lastName: 'Enter a last name of up to 80 characters.',
      userName: 'Use 3–40 letters, numbers, dots, underscores or hyphens.',
      driveLicense: 'Enter the 11-digit CNH registration number.',
      email: 'Enter a valid email address.',
      password:
        'Choose a nonblank password. Use at least 12 characters and at most 72 UTF-8 bytes.',
      passwordConfirmation: 'Passwords do not match.',
    })
  })

  scenario('Given one field has multiple issues, when checked, then show one field message', () => {
    const errors = registrationErrors({
      ...valid,
      password: ' '.repeat(11),
      passwordConfirmation: ' '.repeat(11),
    })
    expect(errors).toEqual({
      password:
        'Choose a nonblank password. Use at least 12 characters and at most 72 UTF-8 bytes.',
    })
  })
})
