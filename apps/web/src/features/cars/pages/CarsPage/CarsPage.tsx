import { Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { PageSection } from '../../../../components/PageSection/PageSection'
import { listCars } from '../../cars-api'
import { formatBrl } from '../../brl'

export function CarsPage() {
  const cars = useQuery({ queryKey: ['cars'], queryFn: ({ signal }) => listCars(signal) })
  return (
    <PageSection
      eyebrow="Fleet"
      title="Cars"
      description="Browse the rental fleet and manage individual vehicles."
      action={
        <Link className="btn-primary" to="/cars/new">
          New car
        </Link>
      }
    >
      {cars.isPending ? (
        <p role="status">Loading cars…</p>
      ) : cars.isError ? (
        <div role="alert">
          {cars.error.message}{' '}
          <button className="btn-secondary" onClick={() => void cars.refetch()}>
            Try again
          </button>
        </div>
      ) : cars.data.length === 0 ? (
        <p>No cars yet.</p>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {cars.data.map((car) => (
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
    </PageSection>
  )
}
