import { useId, type ComponentPropsWithoutRef } from 'react'
import { useField, type AnyFormApi, type DeepKeysOfType } from '@tanstack/react-form'

type Props<TForm extends AnyFormApi> = Omit<
  ComponentPropsWithoutRef<'input'>,
  'form' | 'name' | 'value' | 'defaultValue' | 'onChange' | 'onBlur' | 'type'
> & {
  form: TForm
  name: DeepKeysOfType<TForm['state']['values'], string> & string
  label?: string
  type?: 'text' | 'email' | 'password' | 'search' | 'tel' | 'url'
}

export function TextField<TForm extends AnyFormApi>({
  form,
  name,
  label,
  id,
  type = 'text',
  maxLength = 80,
  className = 'control',
  'aria-describedby': describedBy,
  ...inputProps
}: Props<TForm>) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const fieldName: string = name
  const field = useField({ form, name: fieldName })
  // TanStack removes empty error-map entries from meta.errors.
  const error = field.state.meta.errors
    .map((issue: string | { message: string }) =>
      typeof issue === 'string' ? issue : issue.message,
    )
    .join(' ')
  const errorId = `${inputId}-error`
  const description =
    [describedBy, error ? errorId : undefined].filter(Boolean).join(' ') || undefined
  const words = name.replace(/([a-z])([A-Z])/g, '$1 $2').toLowerCase()
  const fieldLabel = label ?? words.charAt(0).toUpperCase() + words.slice(1)

  return (
    <div>
      <label htmlFor={inputId}>{fieldLabel}</label>
      <input
        {...inputProps}
        id={inputId}
        name={name}
        type={type}
        className={className}
        maxLength={maxLength}
        value={field.state.value}
        onChange={(event) => field.handleChange(event.target.value)}
        onBlur={field.handleBlur}
        aria-invalid={Boolean(error)}
        aria-describedby={description}
      />
      {error && (
        <span id={errorId} role="alert">
          {error}
        </span>
      )}
    </div>
  )
}
