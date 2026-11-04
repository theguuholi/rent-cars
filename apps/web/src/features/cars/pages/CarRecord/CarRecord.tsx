import { useCurrentAction } from '../../../../routes/use-current-action'
import { Link, useNavigate } from '@tanstack/react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { PageSection } from '../../../../components/PageSection/PageSection'
import { deleteCar, getCar } from '../../cars-api'
import { CarFormPage } from '../CarFormPage/CarFormPage'
import { CarDetails } from '../../components/CarDetails/CarDetails'

export function CarRecord({ id, mode }: { id: string; mode: 'detail' | 'edit' | 'delete' }) {
  const record = useQuery({ queryKey: ['car', id], queryFn: ({ signal }) => getCar(id, signal) })
  const client = useQueryClient()
  const navigate = useNavigate()
  const begin = useCurrentAction()
  const remove = useMutation({
    onMutate: begin,
    mutationFn: (carId: string) => deleteCar(carId),
    onSuccess: async (_, deletedId, current) => {
      await client.invalidateQueries({ queryKey: ['cars'] })
      if (current()) await navigate({ to: '/cars', replace: true })
      client.removeQueries({ queryKey: ['car', deletedId], exact: true })
    },
  })
  const error = record.isError && (
    <div role="alert">
      {record.error.message}{' '}
      <button className="btn-secondary" onClick={() => void record.refetch()}>
        Try again
      </button>
    </div>
  )
  if (mode === 'edit' && record.data)
    return <CarFormPage key={record.data.id} car={record.data} refreshError={error} />
  if (!record.isSuccess)
    return (
      <PageSection eyebrow="Fleet" title="Car details">
        {record.isPending ? <p role="status">Loading car…</p> : error}
        <Link to="/cars">Back to cars</Link>
      </PageSection>
    )
  const car = record.data
  if (mode === 'delete')
    return (
      <PageSection
        eyebrow="Fleet"
        title={`Delete ${car.name}?`}
        description={`Remove the vehicle with plate ${car.licensePlate} from the fleet.`}
      >
        {remove.isError && <p role="alert">{remove.error.message}</p>}
        <div className="flex flex-wrap gap-3">
          <button
            className="btn-danger"
            disabled={remove.isPending}
            onClick={() => remove.mutate(id)}
          >
            Confirm deletion
          </button>
          <Link autoFocus className="btn-secondary" disabled={remove.isPending} to="..">
            Cancel
          </Link>
        </div>
      </PageSection>
    )
  return <CarDetails car={car} />
}
