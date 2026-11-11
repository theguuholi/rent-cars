import { useSession } from '../../../auth/session'
import { Link, useNavigate, useSearch } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { PageSection } from '../../../../components/PageSection/PageSection'
import { listCars } from '../../cars-api'
import { formatBrl } from '../../brl'
import type { CarSearch } from '../../cars-api'

export function CarsPage() {
  const isAdmin = useSession()?.user.role === 'ADMIN'
  const search: CarSearch = useSearch({ from: '/cars' })
  const navigate = useNavigate({ from: '/cars' })
  const cars = useQuery({
    queryKey: ['cars', isAdmin, search],
    queryFn: ({ signal }) => listCars(search, isAdmin, signal),
  })
  const update = (changes: Partial<typeof search>) =>
    void navigate({ to: '/cars', search: { ...search, ...changes, page: changes.page ?? 0 } })
  return (
    <PageSection
      eyebrow="Fleet"
      title="Cars"
      description="Browse the rental fleet and manage individual vehicles."
      action={
        isAdmin && (
          <Link className="btn-primary" to="/cars/new">
            New car
          </Link>
        )
      }
    >
      <div className="mb-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
        <label>
          Name
          <input
            className="input"
            value={search.name}
            onChange={(event) => update({ name: event.target.value })}
          />
        </label>
        <label>
          Brand
          <input
            className="input"
            value={search.brand}
            onChange={(event) => update({ brand: event.target.value })}
          />
        </label>
        <label>
          Category
          <input
            className="input"
            value={search.category}
            onChange={(event) => update({ category: event.target.value })}
          />
        </label>
        <label>
          Sort by
          <select
            className="input"
            value={search.sort}
            onChange={(event) => update({ sort: event.target.value })}
          >
            <option value="name,asc">Name A–Z</option>
            <option value="name,desc">Name Z–A</option>
            <option value="brand,asc">Brand A–Z</option>
            <option value="brand,desc">Brand Z–A</option>
          </select>
        </label>
        <label>
          Cars per page
          <select
            className="input"
            value={search.size}
            onChange={(event) => update({ size: Number(event.target.value) })}
          >
            <option value={1}>1</option>
            <option value={6}>6</option>
            <option value={12}>12</option>
            <option value={24}>24</option>
          </select>
        </label>
      </div>
      {cars.isPending ? (
        <p role="status">Loading cars…</p>
      ) : cars.isError ? (
        <div role="alert">
          {cars.error.message}{' '}
          <button className="btn-secondary" onClick={() => void cars.refetch()}>
            Try again
          </button>
        </div>
      ) : cars.data.content.length === 0 ? (
        <p>No matching cars.</p>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {cars.data.content.map((car) => (
            <li key={car.id} className="rounded-lg border border-slate-200 p-4">
              <Link
                className="text-xl font-bold text-brand wrap-anywhere"
                to="/cars/$id"
                params={{ id: car.id }}
              >
                {car.name}
              </Link>
              <p>
                {car.brand} · {car.licensePlate}
              </p>
              <p>{formatBrl(car.dailyRateCents)} per day</p>
              <p>{car.available ? 'Available' : 'Unavailable'}</p>
            </li>
          ))}
        </ul>
      )}
      {cars.data && cars.data.totalPages > 0 && (
        <nav className="mt-5 flex items-center gap-3" aria-label="Car pages">
          <button
            className="btn-secondary"
            disabled={search.page === 0}
            onClick={() => update({ page: search.page - 1 })}
          >
            Previous page
          </button>
          <span>
            Page {cars.data.number + 1} of {cars.data.totalPages} ({cars.data.totalElements} cars)
          </span>
          <button
            className="btn-secondary"
            disabled={search.page + 1 >= cars.data.totalPages}
            onClick={() => update({ page: search.page + 1 })}
          >
            Next page
          </button>
        </nav>
      )}
    </PageSection>
  )
}
