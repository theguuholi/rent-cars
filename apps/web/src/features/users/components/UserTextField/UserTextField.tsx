import { useId, type InputHTMLAttributes } from 'react'

type Props = {
  label: string
  error?: string
  input: InputHTMLAttributes<HTMLInputElement>
}

export function UserTextField({ label, error, input }: Props) {
  const errorId = useId()
  return (
    <label>
      {label}
      <input
        {...input}
        aria-label={label}
        aria-describedby={error ? errorId : undefined}
        aria-invalid={Boolean(error)}
        className="control"
      />
      {error && (
        <span id={errorId} role="alert">
          {error}
        </span>
      )}
    </label>
  )
}
