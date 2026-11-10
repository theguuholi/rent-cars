import { z } from 'zod'
import type { RegistrationInput } from '../../auth-api'

const firstNameError = 'Enter a first name of up to 80 characters.'
const lastNameError = 'Enter a last name of up to 80 characters.'
const userNameError = 'Use 3–40 letters, numbers, dots, underscores or hyphens.'
const driveLicenseError = 'Enter the 11-digit CNH registration number.'
const emailError = 'Enter a valid email address.'
export const passwordHint = 'Use at least 12 characters and at most 72 UTF-8 bytes.'
const passwordError = 'Choose a nonblank password. ' + passwordHint

export const registrationSchema = z
  .object({
    firstName: z.string().trim().min(1, firstNameError).max(80, firstNameError),
    lastName: z.string().trim().min(1, lastNameError).max(80, lastNameError),
    userName: z
      .string()
      .trim()
      .toLowerCase()
      .regex(/^[a-z0-9._-]{3,40}$/, userNameError),
    driveLicense: z
      .string()
      .trim()
      .regex(/^[0-9]{11}$/, driveLicenseError),
    email: z
      .string()
      .regex(/^[^\s@]+@[^\s@]+\.[^\s@]+$/, emailError)
      .max(254, emailError),
    password: z
      .string()
      .min(12, passwordError)
      .refine((value) => value.trim().length > 0, passwordError)
      .refine((value) => new TextEncoder().encode(value).length <= 72, passwordError),
    passwordConfirmation: z.string(),
  })
  .refine(
    ({ password, passwordConfirmation }) =>
      Boolean(passwordConfirmation) && passwordConfirmation === password,
    { path: ['passwordConfirmation'], error: 'Passwords do not match.' },
  )

export type RegistrationErrors = Partial<Record<keyof RegistrationInput, string>>

export function registrationErrors(input: RegistrationInput): RegistrationErrors {
  const result = registrationSchema.safeParse(input)
  if (result.success) return {}

  const errors: RegistrationErrors = {}
  for (const issue of result.error.issues) {
    const field = issue.path[0] as keyof RegistrationInput
    errors[field] = issue.message
  }
  return errors
}
