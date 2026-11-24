import { Link } from '@tanstack/react-router'
import type { Car } from '../../cars-api'
import { formatBrl } from '../../brl'

export function CarCard({ car }: { car: Car }) {
  return (
    <article className="group grid h-full grid-cols-[0.8fr_1.2fr] overflow-hidden rounded border border-line bg-white motion-safe:transition-colors hover:border-brand/40">
      <div className="flex items-center justify-center bg-surface p-3" aria-hidden="true">
        <img src="/car-placeholder.svg" alt="" className="h-24 w-full object-contain" />
      </div>
      <div className="min-w-0 space-y-2 p-4">
        <div>
          <p className="text-sm text-muted wrap-anywhere">{car.brand}</p>
          <Link
            className="font-heading text-xl font-semibold wrap-anywhere hover:text-brand"
            to="/cars/$id"
            params={{ id: car.id }}
          >
            {car.name}
          </Link>
        </div>
        <p className="text-sm text-muted">{car.licensePlate}</p>
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line pt-3">
          <p>
            <strong className="font-heading text-lg font-semibold text-brand">
              {formatBrl(car.dailyRateCents)}
            </strong>{' '}
            <span className="text-xs text-muted">per day</span>
          </p>
          <span
            data-available={car.available}
            className="inline-flex items-center gap-2 text-xs text-muted before:size-1.5 before:rounded-full before:bg-muted data-[available=true]:before:bg-green-700"
          >
            {car.available ? 'Available' : 'Unavailable'}
          </span>
        </div>
      </div>
    </article>
  )
}
