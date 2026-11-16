import { Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { PageSection } from '../../../../components/PageSection/PageSection'
import { formatBrl } from '../../../cars/brl'
import { listRentals } from '../../rentals-api'

export function MyRentalsPage() {
  const rentals = useQuery({ queryKey: ['rentals'], queryFn: ({ signal }) => listRentals(signal) })
  return (
    <PageSection
      eyebrow="Driver"
      title="My rentals"
      description="Your open rentals and receipts for returned cars."
    >
      {rentals.isPending && <p role="status">Loading your rentals…</p>}
      {rentals.isError && (
        <div role="alert">
          {rentals.error.message}{' '}
          <button className="btn-secondary" onClick={() => void rentals.refetch()}>
            Try again
          </button>
        </div>
      )}
      {rentals.isSuccess && rentals.data.length === 0 && <p>No rentals yet.</p>}
      {rentals.isSuccess && (
        <div className="space-y-4">
          {rentals.data.map((rental) => (
            <article
              key={rental.id}
              aria-label={`${rental.car.name} rental`}
              className="rounded-lg border border-slate-200 p-5"
            >
              <div className="flex flex-wrap items-center justify-between gap-3">
                <h3 className="text-xl font-semibold">
                  <Link to="/cars/$id" params={{ id: rental.carId }}>
                    {rental.car.name}
                  </Link>
                </h3>
                <span>{rental.endDate ? 'Returned' : 'Open'}</span>
              </div>
              <dl className="mt-4 grid gap-2 sm:grid-cols-2">
                <div>
                  <dt>Started</dt>
                  <dd>{new Date(rental.startDate).toLocaleString()}</dd>
                </div>
                <div>
                  <dt>Expected return</dt>
                  <dd>{new Date(rental.expectedReturnDate).toLocaleString()}</dd>
                </div>
                {rental.endDate && (
                  <div>
                    <dt>Returned at</dt>
                    <dd>{new Date(rental.endDate).toLocaleString()}</dd>
                  </div>
                )}
                {rental.dailyRateCents !== null && (
                  <div>
                    <dt>Agreed daily rate</dt>
                    <dd>{formatBrl(rental.dailyRateCents)}</dd>
                  </div>
                )}
              </dl>
              {rental.endDate ? (
                <section aria-label="Receipt" className="mt-4 border-t border-slate-200 pt-4">
                  {rental.dailyRateCents !== null &&
                  rental.fineAmountCents !== null &&
                  rental.billedDays !== null &&
                  rental.lateDays !== null ? (
                    <dl className="space-y-1">
                      <div>
                        <dt>Daily charge</dt>
                        <dd>
                          {rental.billedDays} completed day(s) × {formatBrl(rental.dailyRateCents)}
                        </dd>
                      </div>
                      <div>
                        <dt>Late fee</dt>
                        <dd>
                          {rental.lateDays} completed day(s) × {formatBrl(rental.fineAmountCents)}
                        </dd>
                      </div>
                    </dl>
                  ) : (
                    <p>Historical charge breakdown is unavailable.</p>
                  )}
                  {rental.totalCents !== null && (
                    <p className="mt-3 font-semibold">
                      Total charge: {formatBrl(rental.totalCents)}
                    </p>
                  )}
                </section>
              ) : (
                <p className="mt-4">Final charge is calculated on return.</p>
              )}
            </article>
          ))}
        </div>
      )}
    </PageSection>
  )
}
