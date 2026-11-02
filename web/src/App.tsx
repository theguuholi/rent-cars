import { Link, Outlet } from '@tanstack/react-router'
import { CategoryList } from './features/categories/CategoryList'

export function Layout() {
  return (
    <main>
      <h1>Rent Cars</h1>
      <nav className="catalog-nav" aria-label="Vehicle catalogs">
        <Link to="/" activeOptions={{ exact: true }}>
          Home
        </Link>
        <Link to="/categories">Categories</Link>
        <Link to="/specifications">Specifications</Link>
      </nav>
      <Outlet />
    </main>
  )
}

export function CategoriesPage() {
  return (
    <section aria-labelledby="categories-title">
      <h2 id="categories-title">Vehicle categories</h2>
      <CategoryList />
    </section>
  )
}
