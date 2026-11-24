import { FormSection } from '../../../../components/FormSection/FormSection'
import { Button } from '../../../../components/Button/Button'
import { useForm } from '@tanstack/react-form'
import { TextField } from '../../../../components/TextField/TextField'
import type { Category } from '../../../categories/categories-api'
import type { Specification } from '../../../specifications/specifications-api'
import { formatBrlInput } from '../../brl'
import type { Car, CarInput } from '../../cars-api'
import { carDraftSchema, carFieldSchemas, type CarDraft } from './car-draft-schema'

type Props = {
  car?: Car
  categories: Category[]
  specifications: Specification[]
  busy: boolean
  onSave: (input: CarInput) => void
  onCancel: () => void
}

const textFields = [
  ['name', 'Name'],
  ['brand', 'Brand'],
  ['description', 'Description'],
  ['licensePlate', 'License plate'],
] as const
const rates = [
  ['dailyRate', 'Daily rate (BRL)'],
  ['fineAmount', 'Fine amount (BRL)'],
] as const

export function CarEditor({ car, categories, specifications, busy, onSave, onCancel }: Props) {
  const defaultValues: CarDraft = {
    name: car?.name ?? '',
    description: car?.description ?? '',
    brand: car?.brand ?? '',
    licensePlate: car?.licensePlate ?? '',
    available: car?.available ?? true,
    categoryId: car?.categoryId ?? '',
    specificationIds: car?.specificationIds ?? [],
    dailyRate: car ? formatBrlInput(car.dailyRateCents) : '',
    fineAmount: car ? formatBrlInput(car.fineAmountCents) : '',
  }
  const form = useForm({
    defaultValues,
    onSubmit: ({ value }) => onSave(carDraftSchema.parse(value)),
  })
  return (
    <form
      className="max-w-4xl rounded border border-line bg-white p-5 sm:p-8"
      noValidate
      onSubmit={(event) => {
        event.preventDefault()
        void form.handleSubmit()
      }}
    >
      <fieldset className="min-w-0 space-y-7" disabled={busy}>
        <FormSection title="Vehicle">
          {textFields.map(([name, label]) => (
            <TextField
              key={name}
              form={form}
              name={name}
              schema={carFieldSchemas[name]}
              label={label}
              id={name}
              maxLength={null}
              autoFocus={name === 'name'}
              required
              readOnly={!!car && name === 'licensePlate'}
            />
          ))}
        </FormSection>
        <FormSection title="Pricing">
          {rates.map(([name, label]) => (
            <TextField
              key={name}
              form={form}
              name={name}
              schema={carFieldSchemas[name]}
              label={label}
              id={name}
              maxLength={null}
              inputMode="decimal"
              placeholder="0,00"
              required
            />
          ))}
        </FormSection>
        <FormSection title="Availability & equipment">
          <form.Field name="categoryId" validators={{ onChange: carFieldSchemas.categoryId }}>
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
                <span id="category-error">{field.state.meta.errors[0]?.message}</span>
              </div>
            )}
          </form.Field>
          <form.Field name="available">
            {(field) => (
              <label className="self-center">
                <input
                  className="mr-2 align-middle"
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
                  <label className="mr-5 mt-3 inline-flex items-center" key={spec.id}>
                    <input
                      className="mr-2 align-middle"
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
        </FormSection>
        <div className="flex flex-wrap justify-end gap-3 border-t border-line pt-6">
          <Button type="submit" variant="primary">
            {busy ? 'Saving…' : 'Save car'}
          </Button>
          <Button type="button" variant="secondary" onClick={onCancel}>
            Cancel
          </Button>
        </div>
      </fieldset>
    </form>
  )
}
