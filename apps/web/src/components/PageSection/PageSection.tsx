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
    <section className="min-w-0 py-8 sm:py-10" aria-labelledby={headingId}>
      <header className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="mb-2 text-xs font-medium text-muted">{eyebrow}</p>
          <h2
            id={headingId}
            className="font-heading text-3xl font-semibold tracking-tight text-ink sm:text-4xl"
          >
            {title}
          </h2>
          {description && <p className="mt-1 text-muted">{description}</p>}
        </div>
        {action}
      </header>
      {children}
    </section>
  )
}
