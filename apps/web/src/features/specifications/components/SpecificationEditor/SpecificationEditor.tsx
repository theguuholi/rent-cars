import { Button } from '../../../../components/Button/Button'
import { useState } from 'react'
import { useForm } from '@tanstack/react-form'
import { TextField } from '../../../../components/TextField/TextField'
import type { Specification, SpecificationEdit } from '../../specifications-api'
import { specificationDraftSchema } from './specification-draft-schema'

type Props = {
  item?: Specification
  busy: boolean
  onCancel: () => void
  onSave: (input: SpecificationEdit) => void
}

export function SpecificationEditor({ item, busy, onCancel, onSave }: Props) {
  const [original] = useState(item)
  const form = useForm({
    defaultValues: { name: original?.name ?? '', description: original?.description ?? '' },
    onSubmit: ({ value }) => {
      const input = specificationDraftSchema.parse(value)
      if (!original) return onSave(input)
      const changes: SpecificationEdit = {}
      for (const key of ['name', 'description'] as const) {
        if (input[key] !== original[key].trim()) {
          changes[key] = input[key]
        }
      }
      onSave({ ...changes, expectedVersion: original.version })
    },
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
        <TextField
          form={form}
          name="name"
          schema={specificationDraftSchema.shape.name}
          id="spec-name"
          maxLength={null}
          autoFocus
          required
        />
        <form.Field
          name="description"
          validators={{ onChange: specificationDraftSchema.shape.description }}
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
              {field.state.meta.errors[0] && (
                <p id="spec-description-error" role="alert">
                  {field.state.meta.errors[0].message}
                </p>
              )}
            </div>
          )}
        </form.Field>
        <div className="grid gap-2">
          <Button variant="primary" type="submit">
            {busy ? 'Saving…' : 'Save specification'}
          </Button>
          <Button variant="secondary" type="button" onClick={onCancel}>
            Cancel
          </Button>
        </div>
      </fieldset>
    </form>
  )
}
