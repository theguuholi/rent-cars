import type { ReactNode } from 'react'

export function FormSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <fieldset className="min-w-0 pb-2">
      <legend className="mb-5 font-heading text-xl font-semibold text-ink">{title}</legend>
      <div className="grid gap-5 sm:grid-cols-2">{children}</div>
    </fieldset>
  )
}
