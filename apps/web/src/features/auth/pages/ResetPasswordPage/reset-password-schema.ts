import { z } from 'zod'
import { passwordSchema } from '../../password-schema'

export const resetPasswordSchema = z
  .object({
    password: passwordSchema,
    passwordConfirmation: z.string().min(1, 'Confirm the new password.'),
  })
  .refine(({ password, passwordConfirmation }) => password === passwordConfirmation, {
    path: ['passwordConfirmation'],
    error: 'Passwords do not match.',
  })

export type ResetPasswordErrors = Partial<Record<'password' | 'passwordConfirmation', string>>

export function parseResetPassword(input: { password: string; passwordConfirmation: string }) {
  const result = resetPasswordSchema.safeParse(input)
  if (result.success) return { data: result.data, errors: {} as ResetPasswordErrors }
  const errors: ResetPasswordErrors = {}
  for (const issue of result.error.issues) {
    const field = issue.path[0] as keyof ResetPasswordErrors
    if (!errors[field]) errors[field] = issue.message
  }
  return { data: null, errors }
}
