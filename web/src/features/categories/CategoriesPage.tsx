import { CategoryList } from './CategoryList'

export function CategoriesPage() {
  return (
    <section className="py-8" aria-labelledby="categories-title">
      <p className="eyebrow">Catalog</p>
      <h2 className="page-title mb-6" id="categories-title">
        Vehicle categories
      </h2>
      <CategoryList />
    </section>
  )
}
