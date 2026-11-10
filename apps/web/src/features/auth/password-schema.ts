import { z } from 'zod'

export const passwordHint = 'Use at least 12 characters and at most 72 UTF-8 bytes.'
const passwordError = 'Choose a nonblank password. ' + passwordHint

export const passwordSchema = z
  .string()
  .min(12, passwordError)
  .refine((value) => value.trim().length > 0, passwordError)
  .refine((value) => new TextEncoder().encode(value).length <= 72, passwordError)
