import { useForm } from '@tanstack/react-form'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSession } from '../../../auth/session'
import { listRentals, rentCar, returnRental } from '../../rentals-api'

export function RentalActions({ carId, available }: { carId: string; available: boolean }) {
  const identity = useSession()
  const client = useQueryClient()
  const rentals = useQuery({
    queryKey: ['rentals'],
    queryFn: ({ signal }) => listRentals(signal),
    enabled: identity?.user.role === 'USER',
  })
  const refresh = async () => {
    await Promise.all([
      client.invalidateQueries({ queryKey: ['rentals'] }),
      client.invalidateQueries({ queryKey: ['car', carId], exact: true }),
      client.invalidateQueries({ queryKey: ['cars'] }),
    ])
  }
  const rent = useMutation({
    mutationFn: (expectedReturnDate: string) => rentCar(carId, expectedReturnDate),
    onSuccess: async () => {
      await refresh()
      form.reset()
    },
  })
  const giveBack = useMutation({
    mutationFn: (id: string) => returnRental(id),
    onSuccess: refresh,
  })
  const form = useForm({
    // Stryker disable next-line StringLiteral: datetime-local sanitizes a non-date default to empty.
    defaultValues: { expectedReturnDate: '' },
    onSubmit: ({ value }) => rent.mutate(new Date(value.expectedReturnDate).toISOString()),
  })

  if (identity?.user.role !== 'USER') return null
  const open = rentals.data?.find((rental) => rental.carId === carId && !rental.endDate)
  return (
    <section aria-labelledby="rental-actions-title" className="mt-8 space-y-4">
      <h2 id="rental-actions-title" className="text-xl font-semibold">
        Rental
      </h2>
      {rentals.isPending && <p role="status">Loading your rentals…</p>}
      {rentals.isError && (
        <div role="alert">
          {rentals.error.message}{' '}
          <button className="btn-secondary" onClick={() => void rentals.refetch()}>
            Try again
          </button>
        </div>
      )}
      {open && (
        <div className="space-y-3">
          <p>
            Your rental is open. Expected return:{' '}
            {new Date(open.expectedReturnDate).toLocaleString()}.
          </p>
          <button
            className="btn-primary"
            disabled={giveBack.isPending}
            onClick={() => {
              rent.reset()
              giveBack.mutate(open.id)
            }}
          >
            {giveBack.isPending ? 'Returning…' : 'Return car'}
          </button>
        </div>
      )}
      {rentals.isSuccess && !open && !available && <p>This car is unavailable to rent.</p>}
      {rentals.isSuccess && !open && available && (
        <form
          aria-label="Rent this car"
          noValidate
          onSubmit={(event) => {
            event.preventDefault()
            void form.handleSubmit()
          }}
        >
          <fieldset disabled={rent.isPending} className="space-y-3">
            <form.Field
              name="expectedReturnDate"
              validators={{
                onChange: ({ value }) => {
                  const date = new Date(value)
                  if (!value || !Number.isFinite(date.getTime()))
                    return 'Choose a return date and time.'
                  if (date.getTime() < Date.now() + 24 * 60 * 60 * 1000)
                    return 'Choose a return time at least 24 hours from now.'
                },
              }}
            >
              {(field) => (
                <div>
                  <label htmlFor="rental-return-date">Expected return</label>
                  <input
                    className="control"
                    id="rental-return-date"
                    type="datetime-local"
                    required
                    value={field.state.value}
                    onChange={(event) => {
                      rent.reset()
                      giveBack.reset()
                      field.handleChange(event.target.value)
                    }}
                    aria-invalid={field.state.meta.errors.length > 0}
                    aria-describedby="rental-return-help rental-return-error"
                  />
                  <p id="rental-return-help">
                    Choose a local date and time at least 24 hours ahead.
                  </p>
                  <p id="rental-return-error" role="alert">
                    {field.state.meta.errors[0]}
                  </p>
                </div>
              )}
            </form.Field>
            <button className="btn-primary" type="submit">
              {rent.isPending ? 'Renting…' : 'Rent car'}
            </button>
          </fieldset>
        </form>
      )}
      {rent.isError && <p role="alert">{rent.error.message}</p>}
      {giveBack.isError && <p role="alert">{giveBack.error.message}</p>}
      {giveBack.isSuccess && <p role="status">Car returned.</p>}
      {rent.isSuccess && <p role="status">Car rented.</p>}
    </section>
  )
}
