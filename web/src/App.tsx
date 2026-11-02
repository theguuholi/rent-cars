import { useState } from 'react'
import { Specifications } from './features/specifications/Specifications'
import { CategoryList } from './features/categories/CategoryList'

function App() {
  const [catalog, setCatalog] = useState<'categories' | 'specifications'>('categories')
  return (
    <main>
      <h1>Rent Cars</h1>
      <nav className="catalog-nav" aria-label="Vehicle catalogs">
        <button aria-pressed={catalog === 'categories'} onClick={() => setCatalog('categories')}>
          Categories
        </button>
        <button
          aria-pressed={catalog === 'specifications'}
          onClick={() => setCatalog('specifications')}
        >
          Specifications
        </button>
      </nav>

      {catalog === 'categories' ? <CategoryList /> : <Specifications />}
    </main>
  )
}

export default App
