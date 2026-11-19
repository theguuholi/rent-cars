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
