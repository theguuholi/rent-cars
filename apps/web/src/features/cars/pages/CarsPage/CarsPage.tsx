import { Button } from '../../../../components/Button/Button'
import { MostRentedCars } from '../../components/MostRentedCars/MostRentedCars'
import { useSession } from '../../../auth/session'
import { Link, getRouteApi } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { PageSection } from '../../../../components/PageSection/PageSection'
import { listCars } from '../../cars-api'
import { CarCard } from '../../components/CarCard/CarCard'
import type { CarSearch } from '../../cars-api'

const carsRoute = getRouteApi('/cars')

export function CarsPage() {
  const isAdmin = useSession()?.user.role === 'ADMIN'
  const routeSearch = carsRoute.useSearch()
  const search: CarSearch = {
    name: routeSearch.name ?? '',
    brand: routeSearch.brand ?? '',
    category: routeSearch.category ?? '',
    page: routeSearch.page ?? 0,
    size: routeSearch.size ?? 12,
    sort: routeSearch.sort ?? 'name,asc',
  }
  const navigate = carsRoute.useNavigate()
  const cars = useQuery({
    queryKey: ['cars', isAdmin, search],
    queryFn: ({ signal }) => listCars(search, isAdmin, signal),
  })
  const update = (changes: Partial<typeof search>) =>
    void navigate({ search: { ...search, ...changes, page: changes.page ?? 0 } })
  return (
    <>
      <MostRentedCars />
      <PageSection
        eyebrow="Fleet"
        title="Cars"
        description="Browse the rental fleet and manage individual vehicles."
        action={
          isAdmin && (
            <Link className="action" data-variant="primary" to="/cars/new">
              New car
            </Link>
          )
        }
      >
        <div className="mb-6 grid gap-4 rounded border border-line bg-white p-5 sm:grid-cols-2 lg:grid-cols-5">
          <label>
            Name
            <input
              className="control"
              value={search.name}
              onChange={(event) => update({ name: event.target.value })}
            />
          </label>
          <label>
            Brand
            <input
              className="control"
              value={search.brand}
              onChange={(event) => update({ brand: event.target.value })}
            />
          </label>
          <label>
            Category
            <input
              className="control"
              value={search.category}
              onChange={(event) => update({ category: event.target.value })}
            />
          </label>
          <label>
            Sort by
            <select
              className="control"
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
              className="control"
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
            <Button variant="secondary" onClick={() => void cars.refetch()}>
              Try again
            </Button>
          </div>
        ) : cars.data.content.length === 0 ? (
          <p>No matching cars.</p>
        ) : (
          <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {cars.data.content.map((car) => (
              <li key={car.id}>
                <CarCard car={car} />
              </li>
            ))}
          </ul>
        )}
        {cars.data && cars.data.totalPages > 0 && (
          <nav
            className="mt-6 flex flex-wrap items-center justify-center gap-3 text-sm"
            aria-label="Car pages"
          >
            <Button
              variant="secondary"
              disabled={search.page === 0}
              onClick={() => update({ page: search.page - 1 })}
            >
              Previous page
            </Button>
            <span>
              Page {cars.data.number + 1} of {cars.data.totalPages} ({cars.data.totalElements} cars)
            </span>
            <Button
              variant="secondary"
              disabled={search.page + 1 >= cars.data.totalPages}
              onClick={() => update({ page: search.page + 1 })}
            >
              Next page
            </Button>
          </nav>
        )}
      </PageSection>
    </>
  )
}
