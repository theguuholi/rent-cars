import { Link, Outlet } from '@tanstack/react-router'
import { CategoryList } from './features/categories/CategoryList'

export function Layout() {
  return (
    <>
      <header className="border-b border-slate-200">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-8 px-4 sm:px-8">
          <h1 className="py-3 text-2xl font-black tracking-tight">
            <Link to="/" aria-label="Rent Cars home">
              Rent<span className="text-orange-600">Cars</span>
            </Link>
          </h1>
          <nav className="catalog-nav flex gap-5" aria-label="Vehicle catalogs">
            <Link to="/categories">Categories</Link>
            <Link to="/specifications">Specifications</Link>
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-7xl px-4 sm:px-8">
        <Outlet />
      </main>
    </>
  )
}

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
