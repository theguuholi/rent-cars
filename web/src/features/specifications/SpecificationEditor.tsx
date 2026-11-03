import { useForm } from '@tanstack/react-form'
import type { Specification, SpecificationInput } from './specifications-api'

type Props = {
  item?: Specification
  busy: boolean
  onCancel: () => void
  onSave: (input: SpecificationInput) => void
}

export function SpecificationEditor({ item, busy, onCancel, onSave }: Props) {
  const form = useForm({
    defaultValues: { name: item?.name ?? '', description: item?.description ?? '' },
    onSubmit: ({ value }) =>
      onSave({ name: value.name.trim(), description: value.description.trim() }),
  })
  return (
    <form
      noValidate
      onSubmit={(event) => {
        event.preventDefault()
        void form.handleSubmit()
      }}
    >
      <fieldset className="grid min-w-0 gap-5" disabled={busy}>
        <legend className="mb-5 text-2xl font-bold tracking-tight">
          {item ? 'Edit specification' : 'New specification'}
        </legend>
        <form.Field
          name="name"
          validators={{
            onChange: ({ value }) => {
              if (!value.trim()) return 'Enter a name.'
              if (value.trim().toUpperCase().length > 255)
                return 'Use at most 255 characters after uppercase conversion.'
            },
          }}
        >
          {(field) => (
            <div>
              <label htmlFor="spec-name">Name</label>
              <input
                className="control"
                autoFocus
                id="spec-name"
                name={field.name}
                required
                value={field.state.value}
                onChange={(event) => field.handleChange(event.target.value)}
                aria-invalid={!field.state.meta.isValid}
                aria-describedby={!field.state.meta.isValid ? 'spec-name-error' : undefined}
              />
              {!field.state.meta.isValid && (
                <p id="spec-name-error" role="alert">
                  {field.state.meta.errors[0]}
                </p>
              )}
            </div>
          )}
        </form.Field>
        <form.Field
          name="description"
          validators={{
            onChange: ({ value }) => (value.trim() ? undefined : 'Enter a description.'),
          }}
        >
          {(field) => (
            <div>
              <label htmlFor="spec-description">Description</label>
              <textarea
                className="control"
                rows={4}
                id="spec-description"
                name={field.name}
                required
                value={field.state.value}
                onChange={(event) => field.handleChange(event.target.value)}
                aria-invalid={!field.state.meta.isValid}
                aria-describedby={!field.state.meta.isValid ? 'spec-description-error' : undefined}
              />
              {!field.state.meta.isValid && (
                <p id="spec-description-error" role="alert">
                  {field.state.meta.errors[0]}
                </p>
              )}
            </div>
          )}
        </form.Field>
        <div className="grid gap-2">
          <button className="btn-primary" type="submit">
            {busy ? 'Saving…' : 'Save specification'}
          </button>
          <button className="btn-secondary" type="button" onClick={onCancel}>
            Cancel
          </button>
        </div>
      </fieldset>
    </form>
  )
}
