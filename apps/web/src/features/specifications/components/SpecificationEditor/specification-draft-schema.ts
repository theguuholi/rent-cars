import { z } from 'zod'

const specificationFields = {
  name: z
    .string()
    .trim()
    .min(1, 'Enter a name.')
    .refine(
      (value) => value.toUpperCase().length <= 255,
      'Use at most 255 characters after uppercase conversion.',
    ),
  description: z.string().trim().min(1, 'Enter a description.'),
}

export const specificationDraftSchema = z.object(specificationFields)
