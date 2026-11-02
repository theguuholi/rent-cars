import { Link } from '@tanstack/react-router'

export function NotFoundPage() {
  return (
    <section className="py-8">
      <p className="eyebrow">404</p>
      <h2 className="page-title">Page not found</h2>
      <p className="my-4 text-slate-600">Choose a catalog from the home menu.</p>
      <Link className="font-semibold text-brand underline" to="/">
        Back to home
      </Link>
    </section>
  )
}
