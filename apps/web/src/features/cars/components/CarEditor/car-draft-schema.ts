import { z } from 'zod'
import { parseBrl } from '../../brl'
import type { CarInput } from '../../cars-api'

function requiredText(label: string, maxLength: number) {
  return z
    .string()
    .trim()
    .min(1, `Enter a ${label}.`)
    .max(maxLength, `Use at most ${maxLength} characters.`)
}

const amountError = 'Enter a nonnegative BRL amount with up to two decimal places.'
const amount = z
  .string()
  .refine((value) => parseBrl(value) !== null, amountError)
  .transform((value) => parseBrl(value)!)

const carFieldSchemas = {
  name: requiredText('name', 100),
  description: requiredText('description', 255),
  brand: requiredText('brand', 80),
  licensePlate: z
    .string()
    .trim()
    .toUpperCase()
    .min(1, 'Enter a license plate.')
    .max(20, 'Use at most 20 characters.'),
  dailyRate: amount,
  fineAmount: amount,
  categoryId: z.string().min(1, 'Select a category.'),
}

export const carDraftSchema = z
  .object({
    ...carFieldSchemas,
    available: z.boolean(),
    specificationIds: z.array(z.string()),
  })
  .transform(({ dailyRate, fineAmount, ...fields }): CarInput => ({
    ...fields,
    dailyRateCents: dailyRate,
    fineAmountCents: fineAmount,
  }))

export type CarDraft = z.input<typeof carDraftSchema>
export type CarField = keyof typeof carFieldSchemas

export function carFieldError(field: CarField, value: string): string | undefined {
  const result = carFieldSchemas[field].safeParse(value)
  return result.success ? undefined : result.error.issues[0]?.message
}
