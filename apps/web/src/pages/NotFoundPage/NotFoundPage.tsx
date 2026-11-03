import { PageSection } from '../../components/PageSection/PageSection'
import { Link } from '@tanstack/react-router'

export function NotFoundPage() {
  return (
    <PageSection
      eyebrow="404"
      title="Page not found"
      description="Choose a catalog from the home menu."
    >
      <Link className="font-semibold text-brand underline" to="/">
        Back to home
      </Link>
    </PageSection>
  )
}
