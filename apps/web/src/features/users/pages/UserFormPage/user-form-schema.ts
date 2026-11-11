import { z } from 'zod'
import { registrationSchema } from '../../../auth/pages/RegisterPage/registration-schema'

export const newUserSchema = registrationSchema.safeExtend({ role: z.enum(['USER', 'ADMIN']) })
export type UserDraft = z.input<typeof newUserSchema>

export const editedUserSchema = z.object({
  email: z.email().max(254),
  role: z.enum(['USER', 'ADMIN']),
  firstName: z.string().trim().min(1).max(80).nullable(),
  lastName: z.string().trim().min(1).max(80).nullable(),
  userName: z
    .string()
    .trim()
    .toLowerCase()
    .regex(/^[a-z0-9._-]{3,40}$/)
    .nullable(),
  driveLicense: z
    .string()
    .trim()
    .regex(/^[0-9]{11}$/)
    .nullable(),
})
