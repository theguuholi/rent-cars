import { PageSection } from '../../../../components/PageSection/PageSection'
import { CategoryList } from '../../components/CategoryList/CategoryList'

export function CategoriesPage() {
  return (
    <PageSection eyebrow="Catalog" title="Vehicle categories">
      <CategoryList />
    </PageSection>
  )
}
