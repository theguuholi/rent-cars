import { describe, expect, test as scenario } from 'vitest'
import { newUserSchema, userEditDraftSchema } from './user-form-schema'

const draft = {
  firstName: ' Ada ',
  lastName: ' Lovelace ',
  userName: ' ADA.LOVE ',
  driveLicense: ' 01234567890 ',
  email: 'ada@example.test',
  role: 'ADMIN' as const,
  password: 'long-password-123',
  passwordConfirmation: 'long-password-123',
}

describe('User form schemas', () => {
  scenario(
    'Given a new administrator, when parsed, then normalize the shared account fields and retain the chosen role',
    () => {
      expect(newUserSchema.parse(draft)).toEqual({
        ...draft,
        firstName: 'Ada',
        lastName: 'Lovelace',
        userName: 'ada.love',
        driveLicense: '01234567890',
      })
    },
  )

  scenario(
    'Given an existing profile, when edited without a password, then normalize driver fields and exclude credentials from the replacement',
    () => {
      expect(
        userEditDraftSchema(draft).parse({ ...draft, password: '', passwordConfirmation: '' }),
      ).toEqual({
        firstName: 'Ada',
        lastName: 'Lovelace',
        userName: 'ada.love',
        driveLicense: '01234567890',
        email: draft.email,
        role: 'ADMIN',
      })
    },
  )

  scenario.each(['firstName', 'lastName', 'userName', 'driveLicense'] as const)(
    'Given a legacy null %s, when another field is edited, then retain only that null and normalize the other profile fields',
    (field) => {
      const schema = userEditDraftSchema({ ...draft, [field]: null })
      expect(schema.parse(draft)).toEqual({
        firstName: 'Ada',
        lastName: 'Lovelace',
        userName: 'ada.love',
        driveLicense: '01234567890',
        email: draft.email,
        role: 'ADMIN',
        [field]: null,
      })
    },
  )

  scenario(
    'Given invalid edited identity fields, when validated, then use the same actionable messages as registration',
    () => {
      const result = userEditDraftSchema(draft).safeParse({
        ...draft,
        firstName: '',
        lastName: '',
        userName: '?',
        driveLicense: '?',
      })
      expect(result.error?.flatten().fieldErrors).toEqual({
        firstName: ['Enter a first name of up to 80 characters.'],
        lastName: ['Enter a last name of up to 80 characters.'],
        userName: ['Use 3–40 letters, numbers, dots, underscores or hyphens.'],
        driveLicense: ['Enter the 11-digit CNH registration number.'],
      })
    },
  )
})
