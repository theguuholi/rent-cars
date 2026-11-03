import type { CarInput, CarPatch } from './cars-api'

/** Compare the normalized form submission with the values shown when editing began. */
export function changedCarFields(original: CarInput, submitted: CarInput): CarPatch {
  const changes: CarPatch = {}

  for (const field of ['name', 'description', 'brand'] as const) {
    if (submitted[field] !== original[field].trim()) changes[field] = submitted[field]
  }
  for (const field of ['dailyRateCents', 'fineAmountCents'] as const) {
    if (submitted[field] !== original[field]) changes[field] = submitted[field]
  }
  if (submitted.available !== original.available) changes.available = submitted.available
  if (submitted.categoryId !== original.categoryId) changes.categoryId = submitted.categoryId

  const initialIds = new Set(original.specificationIds)
  if (
    submitted.specificationIds.length !== initialIds.size ||
    submitted.specificationIds.some((id) => !initialIds.has(id))
  ) {
    changes.specificationIds = submitted.specificationIds
  }
  return changes
}
