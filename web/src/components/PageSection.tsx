import { useId, type ReactNode } from 'react'

type Props = {
  eyebrow: string
  title: string
  description?: string
  action?: ReactNode
  children: ReactNode
}

export function PageSection({ eyebrow, title, description, action, children }: Props) {
  const headingId = useId()
  return (
    <section className="py-8" aria-labelledby={headingId}>
      <header className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="mb-2 text-sm font-semibold text-brand">{eyebrow}</p>
          <h2 id={headingId} className="text-3xl font-bold tracking-tight sm:text-4xl">
            {title}
          </h2>
          {description && <p className="mt-1 text-slate-600">{description}</p>}
        </div>
        {action}
      </header>
      {children}
    </section>
  )
}
