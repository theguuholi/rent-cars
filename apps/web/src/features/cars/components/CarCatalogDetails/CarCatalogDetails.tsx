import type { Category } from '../../../categories/categories-api'
import type { Specification } from '../../../specifications/specifications-api'
import type { Car } from '../../cars-api'

type Props = {
  car: Pick<Car, 'categoryId' | 'specificationIds'>
  categories: Category[]
  specifications: Specification[]
}

export function CarCatalogDetails({ car, categories, specifications }: Props) {
  return (
    <dl className="my-4 grid gap-4 sm:grid-cols-2">
      <div>
        <dt>Category</dt>
        <dd>
          {categories.find((category) => category.id === car.categoryId)?.name ?? car.categoryId}
        </dd>
      </div>
      <div>
        <dt>Specifications</dt>
        <dd>
          {car.specificationIds
            .map((specId) => specifications.find((spec) => spec.id === specId)?.name ?? specId)
            .join(', ') || 'None'}
        </dd>
      </div>
    </dl>
  )
}
