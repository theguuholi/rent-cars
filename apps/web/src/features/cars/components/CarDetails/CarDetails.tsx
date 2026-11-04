import { Link } from '@tanstack/react-router'
import { PageSection } from '../../../../components/PageSection/PageSection'
import type { CarWithCatalog } from '../../cars-api'
import { CarFacts } from '../CarFacts/CarFacts'
import { CarCatalogDetails } from '../CarCatalogDetails/CarCatalogDetails'

export function CarDetails({ car }: { car: CarWithCatalog }) {
  return (
    <PageSection
      eyebrow="Fleet · Car details"
      title={car.name}
      description={car.description}
      action={
        <Link className="btn-secondary" to="/cars">
          Back to cars
        </Link>
      }
    >
      <CarFacts car={car} />
      <CarCatalogDetails car={car} />
      <div className="mt-6 flex flex-wrap gap-3">
        <Link className="btn-primary" from="/cars/$id" to="./edit">
          Edit car
        </Link>
        <Link className="btn-secondary" from="/cars/$id" to="./delete">
          Delete car
        </Link>
      </div>
    </PageSection>
  )
}
