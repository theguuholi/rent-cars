import { useSession } from '../../../auth/session'
import { Link } from '@tanstack/react-router'
import { PageSection } from '../../../../components/PageSection/PageSection'
import type { CarWithCatalog } from '../../cars-api'
import { CarImages } from '../CarImages/CarImages'
import { CarFacts } from '../CarFacts/CarFacts'
import { CarCatalogDetails } from '../CarCatalogDetails/CarCatalogDetails'
import { RentalActions } from '../../../rentals/components/RentalActions/RentalActions'

export function CarDetails({ car }: { car: CarWithCatalog }) {
  const isAdmin = useSession()?.user.role === 'ADMIN'
  return (
    <PageSection
      eyebrow="Fleet · Car details"
      title={car.name}
      description={car.description}
      action={
        <Link className="action" data-variant="secondary" to="/cars">
          Back to cars
        </Link>
      }
    >
      <div className="grid items-start gap-8 lg:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)]">
        <CarImages key={car.id} car={car} canUpload={isAdmin} />
        <div className="rounded border border-line bg-white p-6 sm:p-8">
          <CarFacts car={car} />
          <CarCatalogDetails car={car} />
          <RentalActions carId={car.id} available={car.available} />
          {isAdmin && (
            <div className="mt-6 flex flex-wrap gap-3">
              <Link className="action" data-variant="primary" from="/cars/$id" to="./edit">
                Edit car
              </Link>
              <Link className="action" data-variant="secondary" from="/cars/$id" to="./delete">
                Delete car
              </Link>
            </div>
          )}
        </div>
      </div>
    </PageSection>
  )
}
