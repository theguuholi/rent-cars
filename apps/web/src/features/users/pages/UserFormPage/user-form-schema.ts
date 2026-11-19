import { z } from 'zod'
import { registrationSchema } from '../../../auth/registration-schema'

export const newUserSchema = registrationSchema.safeExtend({ role: z.enum(['USER', 'ADMIN']) })
export type UserDraft = z.input<typeof newUserSchema>

export const editedUserSchema = z.object({
  email: z.email().max(254),
  role: z.enum(['USER', 'ADMIN']),
  firstName: registrationSchema.shape.firstName.nullable(),
  lastName: registrationSchema.shape.lastName.nullable(),
  userName: registrationSchema.shape.userName.nullable(),
  driveLicense: registrationSchema.shape.driveLicense.nullable(),
})

// Older accounts may have no driver profile. Keep those fields null when editing.
export function userEditDraftSchema(user: {
  firstName: string | null
  lastName: string | null
  userName: string | null
  driveLicense: string | null
}) {
  return z.preprocess((draft: UserDraft) => {
    return {
      email: draft.email,
      role: draft.role,
      firstName: user.firstName === null ? null : draft.firstName,
      lastName: user.lastName === null ? null : draft.lastName,
      userName: user.userName === null ? null : draft.userName,
      driveLicense: user.driveLicense === null ? null : draft.driveLicense,
    }
  }, editedUserSchema)
}
