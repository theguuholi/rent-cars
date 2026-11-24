import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { PageSection } from '../../../../components/PageSection/PageSection'
import { formatBrl } from '../../brl'
import { listRecommendedCars } from '../../recommendations-api'

export function MostRentedCars() {
  const ranking = useQuery({
    queryKey: ['cars', 'recommendations'],
    queryFn: ({ signal }) => listRecommendedCars(signal),
    retry: false,
  })

  return (
    <PageSection
      eyebrow="Popular choices"
      title="Most rented cars"
      description="Available cars ranked by rentals in the latest 30-day snapshot."
    >
      {ranking.isPending ? (
        <p role="status">Loading recommendations…</p>
      ) : ranking.isError ? (
        <div role="alert">
          Could not load recommendations.{' '}
          <button className="btn-secondary" onClick={() => void ranking.refetch()}>
            Try again
          </button>
        </div>
      ) : ranking.data.length === 0 ? (
        <p>No recommendations yet. Browse the fleet below.</p>
      ) : (
        <ol className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {ranking.data.map((car) => (
            <li key={car.id} className="rounded-lg border border-slate-200 p-4">
              <Link
                className="text-xl font-bold text-brand wrap-anywhere"
                to="/cars/$id"
                params={{ id: car.id }}
              >
                {car.name}
              </Link>
              <p>{car.brand}</p>
              <p>{formatBrl(car.dailyRateCents)} per day</p>
            </li>
          ))}
        </ol>
      )}
    </PageSection>
  )
}
