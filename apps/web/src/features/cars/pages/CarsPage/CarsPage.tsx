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
    q: routeSearch.q ?? '',
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
        <div className="mb-8 space-y-5">
          <label>
            Search cars
            <input
              className="control mt-2 rounded-2xl bg-white px-5"
              placeholder="Search by model, brand or category"
              value={search.q || search.name}
              onChange={(event) => update({ q: event.target.value, name: '' })}
            />
          </label>
          {search.name && (
            <Button
              variant="secondary"
              className="action rounded-full"
              aria-label={`Clear name filter: ${search.name}`}
              onClick={() => update({ name: '' })}
            >
              Model name: {search.name} <span aria-hidden="true">×</span>
            </Button>
          )}
          <div className="flex flex-wrap items-end gap-4">
            <label className="min-w-0 flex-1 basis-36">
              Brand
              <input
                className="control mt-2 bg-white"
                placeholder="Any brand"
                value={search.brand}
                onChange={(event) => update({ brand: event.target.value })}
              />
            </label>
            <label className="min-w-0 flex-1 basis-36">
              Category
              <input
                className="control mt-2 bg-white"
                placeholder="Any category"
                value={search.category}
                onChange={(event) => update({ category: event.target.value })}
              />
            </label>
            <label className="min-w-0 flex-1 basis-36 sm:ml-auto sm:max-w-56">
              Sort by
              <select
                className="control mt-2 bg-white"
                value={search.sort}
                onChange={(event) => update({ sort: event.target.value })}
              >
                <option value="name,asc">Name A–Z</option>
                <option value="name,desc">Name Z–A</option>
                <option value="brand,asc">Brand A–Z</option>
                <option value="brand,desc">Brand Z–A</option>
              </select>
            </label>
          </div>
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
          <ul className="grid gap-6 sm:grid-cols-2 xl:grid-cols-3">
            {cars.data.content.map((car) => (
              <li key={car.id}>
                <CarCard car={car} />
              </li>
            ))}
          </ul>
        )}
        <div className="mt-8 flex flex-wrap items-center justify-between gap-5">
          <label className="flex items-center gap-3 text-muted">
            Cars per page
            <select
              className="control w-auto rounded-full bg-white"
              value={search.size}
              onChange={(event) => update({ size: Number(event.target.value) })}
            >
              <option value={1}>1</option>
              <option value={6}>6</option>
              <option value={12}>12</option>
              <option value={24}>24</option>
            </select>
          </label>
          {cars.data && cars.data.totalPages > 0 && (
            <nav className="flex flex-wrap items-center gap-3 text-sm" aria-label="Car pages">
              <Button
                variant="secondary"
                disabled={search.page === 0}
                onClick={() => update({ page: search.page - 1 })}
              >
                Previous page
              </Button>
              <span>
                Page {cars.data.number + 1} of {cars.data.totalPages} ({cars.data.totalElements}{' '}
                cars)
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
        </div>
      </PageSection>
      <MostRentedCars />
    </>
  )
}
