import type { ReactNode } from 'react'
import { useStore, type AnyFormApi } from '@tanstack/react-form'

export function SubmitButton({ form, children }: { form: AnyFormApi; children: ReactNode }) {
  const submitting = useStore(form.store, (state) => state.isSubmitting)
  return (
    <button className="btn-primary" type="submit" disabled={submitting}>
      {children}
    </button>
  )
}
