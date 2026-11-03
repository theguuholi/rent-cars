import { PageSection } from '../../components/PageSection'
import { CategoryList } from './CategoryList'

export function CategoriesPage() {
  return (
    <PageSection eyebrow="Catalog" title="Vehicle categories">
      <CategoryList />
    </PageSection>
  )
}
