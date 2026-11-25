import { Link } from '@tanstack/react-router'
import { PageSection } from '../../../../components/PageSection/PageSection'
export function ForbiddenPage() {
  return (
    <PageSection
      eyebrow="403"
      title="Access denied"
      description="This page requires an administrator account."
    >
      <Link to="/cars" className="action" data-variant="secondary">
        Browse cars
      </Link>
    </PageSection>
  )
}
