import { describe, expect, test as scenario } from 'vitest'
import { registrationSchema } from './registration-schema'

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
  })

  scenario('Given multiple invalid fields, when checked, then return each field error', () => {
    expect(
      registrationSchema
        .safeParse({
          ...valid,
          firstName: ' ',
          lastName: ' ',
          userName: '@',
          driveLicense: 'invalid',
          email: 'bad',
          password: 'short',
          passwordConfirmation: 'different',
        })
        .error?.flatten().fieldErrors,
    ).toEqual({
      firstName: ['Enter a first name of up to 80 characters.'],
      lastName: ['Enter a last name of up to 80 characters.'],
      userName: ['Use 3–40 letters, numbers, dots, underscores or hyphens.'],
      driveLicense: ['Enter the 11-digit CNH registration number.'],
      email: ['Enter a valid email address.'],
      password: [
        'Choose a nonblank password. Use at least 12 characters and at most 72 UTF-8 bytes.',
      ],
      passwordConfirmation: ['Passwords do not match.'],
    })
  })

  scenario(
    'Given multiple issues on a field, when checked, then retain the schema issues for accessible feedback',
    () => {
      const result = registrationSchema.safeParse({
        ...valid,
        password: ' '.repeat(11),
        passwordConfirmation: ' '.repeat(11),
      })
      expect(result.success).toBe(false)
      expect(result.error?.issues).toHaveLength(2)
      expect(result.error?.issues.every((issue) => issue.path[0] === 'password')).toBe(true)
    },
  )
})
