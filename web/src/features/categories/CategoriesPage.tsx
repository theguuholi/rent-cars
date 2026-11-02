import { CategoryList } from './CategoryList'

export function CategoriesPage() {
  return (
    <section className="page-section" aria-labelledby="categories-title">
      <header className="page-header">
        <div>
          <p className="eyebrow">Catalog</p>
          <h2 className="page-title" id="categories-title">
            Vehicle categories
          </h2>
        </div>
      </header>
      <CategoryList />
    </section>
  )
}
