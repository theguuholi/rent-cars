import { Button } from '../../../../components/Button/Button'
import { useQuery } from '@tanstack/react-query'
import { PageSection } from '../../../../components/PageSection/PageSection'
import { CarCard } from '../CarCard/CarCard'
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
          <Button variant="secondary" onClick={() => void ranking.refetch()}>
            Try again
          </Button>
        </div>
      ) : ranking.data.length === 0 ? (
        <p>No recommendations yet. Browse the fleet below.</p>
      ) : (
        <ol className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {ranking.data.map((car) => (
            <li key={car.id}>
              <CarCard car={car} />
            </li>
          ))}
        </ol>
      )}
    </PageSection>
  )
}
