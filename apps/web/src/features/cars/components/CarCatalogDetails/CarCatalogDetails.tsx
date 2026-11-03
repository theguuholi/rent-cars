import type { CarWithCatalog } from '../../cars-api'

export function CarCatalogDetails({
  car,
}: {
  car: Pick<CarWithCatalog, 'category' | 'specifications'>
}) {
  return (
    <dl className="my-4 grid gap-4 sm:grid-cols-2">
      <div>
        <dt>Category</dt>
        <dd>{car.category.name}</dd>
      </div>
      <div>
        <dt>Specifications</dt>
        <dd>{car.specifications.map((spec) => spec.name).join(', ') || 'None'}</dd>
      </div>
    </dl>
  )
}
