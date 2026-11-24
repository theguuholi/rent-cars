import { Link } from '@tanstack/react-router'
import type { Car } from '../../cars-api'
import { formatBrl } from '../../brl'

export function CarCard({ car }: { car: Car }) {
  return (
    <article className="h-full">
      <Link
        to="/cars/$id"
        params={{ id: car.id }}
        aria-label={car.name}
        className="group flex h-full flex-col overflow-hidden rounded-3xl border border-transparent bg-white shadow-sm motion-safe:transition-[border-color,box-shadow] hover:border-brand/60 hover:shadow-lg focus-visible:border-brand focus-visible:shadow-lg"
      >
        <div
          className="flex aspect-[16/9] items-center justify-center bg-surface p-6"
          aria-hidden="true"
        >
          <img
            src="/car-placeholder.svg"
            alt=""
            className="h-36 w-full object-contain motion-safe:transition-transform motion-safe:group-hover:scale-105 motion-safe:group-focus-visible:scale-105"
          />
        </div>
        <div className="flex flex-1 flex-col gap-4 p-5">
          <div className="min-w-0">
            <p className="text-sm text-muted wrap-anywhere">{car.brand}</p>
            <h3 className="mt-1 font-heading text-xl font-semibold wrap-anywhere group-hover:text-brand group-focus-visible:text-brand">
              {car.name}
            </h3>
            <p className="mt-1 text-xs text-muted">{car.licensePlate}</p>
          </div>
          <span
            data-available={car.available}
            className="inline-flex w-fit items-center gap-2 rounded-full bg-surface px-3 py-1 text-xs text-muted before:size-1.5 before:rounded-full before:bg-muted data-[available=true]:bg-green-50 data-[available=true]:text-green-800 data-[available=true]:before:bg-green-700"
          >
            {car.available ? 'Available' : 'Unavailable'}
          </span>
          <div className="mt-auto flex items-center justify-between gap-3">
            <p>
              <strong className="font-heading text-xl font-semibold text-brand">
                {formatBrl(car.dailyRateCents)}
              </strong>{' '}
              <span className="text-xs text-muted">per day</span>
            </p>
            <span
              aria-hidden="true"
              className="flex size-10 shrink-0 items-center justify-center rounded-full bg-brand-soft text-xl text-brand motion-safe:transition-colors group-hover:bg-brand group-hover:text-white group-focus-visible:bg-brand group-focus-visible:text-white"
            >
              →
            </span>
          </div>
        </div>
      </Link>
    </article>
  )
}
