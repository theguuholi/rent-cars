import { formatBrl } from '../../brl'
import type { Car } from '../../cars-api'

export function CarFacts({ car }: { car: Car }) {
  return (
    <dl className="grid gap-4 sm:grid-cols-2">
      <div>
        <dt>Brand</dt>
        <dd>{car.brand}</dd>
      </div>
      <div>
        <dt>License plate</dt>
        <dd>{car.licensePlate}</dd>
      </div>
      <div>
        <dt>Availability</dt>
        <dd>{car.available ? 'Available' : 'Unavailable'}</dd>
      </div>
      <div>
        <dt>Daily rate</dt>
        <dd className="font-heading text-2xl font-semibold text-brand">
          {formatBrl(car.dailyRateCents)}
        </dd>
      </div>
      <div>
        <dt>Fine amount</dt>
        <dd>{formatBrl(car.fineAmountCents)}</dd>
      </div>
    </dl>
  )
}
