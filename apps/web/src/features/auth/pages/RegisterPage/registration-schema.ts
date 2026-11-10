import { z } from 'zod'
import type { RegistrationInput } from '../../auth-api'
import { passwordSchema, passwordHint } from '../../password-schema'

const firstNameError = 'Enter a first name of up to 80 characters.'
const lastNameError = 'Enter a last name of up to 80 characters.'
const userNameError = 'Use 3–40 letters, numbers, dots, underscores or hyphens.'
const driveLicenseError = 'Enter the 11-digit CNH registration number.'
const emailError = 'Enter a valid email address.'
export { passwordHint }

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
    password: passwordSchema,
    passwordConfirmation: z.string(),
  })
  .refine(
    ({ password, passwordConfirmation }) =>
      Boolean(passwordConfirmation) && passwordConfirmation === password,
    { path: ['passwordConfirmation'], error: 'Passwords do not match.' },
  )

export type RegistrationErrors = Partial<Record<keyof RegistrationInput, string>>

export function parseRegistration(input: RegistrationInput) {
  const result = registrationSchema.safeParse(input)
  if (result.success) return { data: result.data, errors: {} as RegistrationErrors }

  const errors: RegistrationErrors = {}
  for (const issue of result.error.issues) {
    const field = issue.path[0] as keyof RegistrationInput
    errors[field] = issue.message
  }
  return { data: null, errors }
}
