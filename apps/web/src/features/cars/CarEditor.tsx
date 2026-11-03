import { useForm } from '@tanstack/react-form'
import type { Category } from '../categories/categories-api'
import type { Specification } from '../specifications/specifications-api'
import { formatBrlInput, parseBrl } from './brl'
import type { Car, CarInput } from './cars-api'

type Props = {
  car?: Car
  categories: Category[]
  specifications: Specification[]
  busy: boolean
  onSave: (input: CarInput) => void
  onCancel: () => void
}

const textFields = [
  ['name', 'Name', 100],
  ['description', 'Description', 255],
  ['brand', 'Brand', 80],
  ['licensePlate', 'License plate', 20],
] as const
const rates = [
  ['dailyRate', 'Daily rate (BRL)'],
  ['fineAmount', 'Fine amount (BRL)'],
] as const

export function CarEditor({ car, categories, specifications, busy, onSave, onCancel }: Props) {
  const form = useForm({
    defaultValues: {
      name: car?.name ?? '',
      description: car?.description ?? '',
      brand: car?.brand ?? '',
      licensePlate: car?.licensePlate ?? '',
      available: car?.available ?? true,
      categoryId: car?.categoryId ?? '',
      specificationIds: car?.specificationIds ?? ([] as string[]),
      dailyRate: car ? formatBrlInput(car.dailyRateCents) : '',
      fineAmount: car ? formatBrlInput(car.fineAmountCents) : '',
    },
    onSubmit: ({ value }) =>
      onSave({
        name: value.name.trim(),
        description: value.description.trim(),
        brand: value.brand.trim(),
        licensePlate: value.licensePlate.trim().toUpperCase(),
        available: value.available,
        categoryId: value.categoryId,
        specificationIds: value.specificationIds,
        dailyRateCents: parseBrl(value.dailyRate)!,
        fineAmountCents: parseBrl(value.fineAmount)!,
      }),
  })
  return (
    <form
      className="max-w-3xl"
      noValidate
      onSubmit={(event) => {
        event.preventDefault()
        void form.handleSubmit()
      }}
    >
      <fieldset className="grid min-w-0 gap-5 sm:grid-cols-2" disabled={busy}>
        {textFields.map(([name, label, maxLength]) => (
          <form.Field
            key={name}
            name={name}
            validators={{
              onChange: ({ value }) => {
                const normalized =
                  name === 'licensePlate' ? value.trim().toUpperCase() : value.trim()
                if (!normalized) return `Enter a ${label.toLowerCase()}.`
                if (normalized.length > maxLength) return `Use at most ${maxLength} characters.`
              },
            }}
          >
            {(field) => (
              <div>
                <label htmlFor={name}>{label}</label>
                <input
                  className="control"
                  id={name}
                  autoFocus={name === 'name'}
                  required
                  readOnly={!!car && name === 'licensePlate'}
                  value={field.state.value}
                  onChange={(event) => field.handleChange(event.target.value)}
                  aria-invalid={!field.state.meta.isValid}
                  aria-describedby={name + '-error'}
                />
                <span id={name + '-error'}>{field.state.meta.errors[0]}</span>
              </div>
            )}
          </form.Field>
        ))}
        {rates.map(([name, label]) => (
          <form.Field
            name={name}
            key={name}
            validators={{
              onChange: ({ value }) =>
                parseBrl(value) === null
                  ? 'Enter a nonnegative BRL amount with up to two decimal places.'
                  : undefined,
            }}
          >
            {(field) => (
              <div>
                <label htmlFor={name}>{label}</label>
                <input
                  className="control"
                  id={name}
                  inputMode="decimal"
                  placeholder="0,00"
                  required
                  value={field.state.value}
                  onChange={(event) => field.handleChange(event.target.value)}
                  aria-invalid={!field.state.meta.isValid}
                  aria-describedby={name + '-error'}
                />
                <span id={name + '-error'}>{field.state.meta.errors[0]}</span>
              </div>
            )}
          </form.Field>
        ))}
        <form.Field
          name="categoryId"
          validators={{ onChange: ({ value }) => (value ? undefined : 'Select a category.') }}
        >
          {(field) => (
            <div>
              <label htmlFor="car-category">Category</label>
              <select
                id="car-category"
                className="control"
                required
                value={field.state.value}
                aria-invalid={!field.state.meta.isValid}
                aria-describedby="category-error"
                onChange={(event) => field.handleChange(event.target.value)}
              >
                <option value="">Choose a category</option>
                {categories.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
              <span id="category-error">{field.state.meta.errors[0]}</span>
            </div>
          )}
        </form.Field>
        <form.Field name="available">
          {(field) => (
            <label className="self-center">
              <input
                className="mr-1"
                type="checkbox"
                checked={field.state.value}
                onChange={(event) => field.handleChange(event.target.checked)}
              />
              Available
            </label>
          )}
        </form.Field>
        <form.Field name="specificationIds">
          {(field) => (
            <fieldset className="sm:col-span-2">
              <legend>Specifications</legend>
              {specifications.length === 0 && <p>No specifications available.</p>}
              {specifications.map((spec) => (
                <label className="mr-5 inline-block" key={spec.id}>
                  <input
                    className="mr-1"
                    type="checkbox"
                    checked={field.state.value.includes(spec.id)}
                    onChange={(event) =>
                      field.handleChange(
                        event.target.checked
                          ? [...field.state.value, spec.id]
                          : field.state.value.filter((id) => id !== spec.id),
                      )
                    }
                  />
                  {spec.name}
                </label>
              ))}
            </fieldset>
          )}
        </form.Field>
        <div className="flex flex-wrap gap-3 sm:col-span-2">
          <button type="submit" className="btn-primary">
            {busy ? 'Saving…' : 'Save car'}
          </button>
          <button type="button" className="btn-secondary" onClick={onCancel}>
            Cancel
          </button>
        </div>
      </fieldset>
    </form>
  )
}
