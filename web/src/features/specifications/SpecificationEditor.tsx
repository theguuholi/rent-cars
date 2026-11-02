import type { FormEvent } from 'react'
import type { Specification, SpecificationInput } from './specifications-api'

type Props = {
  item?: Specification
  busy: boolean
  onCancel: () => void
  onSave: (input: SpecificationInput) => void
}

export function SpecificationEditor({ item, busy, onCancel, onSave }: Props) {
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const fields = new FormData(event.currentTarget)
    onSave({
      name: String(fields.get('name')).trim(),
      description: String(fields.get('description')).trim(),
    })
  }

  return (
    <form onSubmit={submit}>
      <fieldset disabled={busy}>
        <legend>{item ? 'Edit specification' : 'New specification'}</legend>
        <label>
          Name
          <input
            name="name"
            required
            maxLength={255}
            pattern={'.*\\S.*'}
            defaultValue={item?.name}
          />
        </label>
        <label>
          Description
          <textarea name="description" required defaultValue={item?.description} />
        </label>
        <div className="actions">
          <button type="submit">{busy ? 'Saving…' : 'Save specification'}</button>
          <button type="button" onClick={onCancel}>
            Cancel
          </button>
        </div>
      </fieldset>
    </form>
  )
}
