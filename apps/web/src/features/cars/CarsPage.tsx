import { Link, useNavigate } from '@tanstack/react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { PageSection } from '../../components/PageSection'
import { listCategories } from '../categories/categories-api'
import { listSpecifications } from '../specifications/specifications-api'
import { CarFacts } from './components/CarFacts/CarFacts'
import { CarCatalogDetails } from './components/CarCatalogDetails/CarCatalogDetails'
import { CarEditor } from './CarEditor'
import {
  createCar,
  deleteCar,
  getCar,
  listCars,
  updateCar,
  type Car,
  type CarInput,
} from './cars-api'
import { formatBrl } from './brl'

export function CarsPage() {
  const cars = useQuery({ queryKey: ['cars'], queryFn: ({ signal }) => listCars(signal) })
  return (
    <PageSection
      eyebrow="Fleet"
      title="Cars"
      description="Browse the rental fleet and manage individual vehicles."
      action={
        <Link className="btn-primary" to="/cars/new">
          New car
        </Link>
      }
    >
      {cars.isPending ? (
        <p role="status">Loading cars…</p>
      ) : cars.isError ? (
        <div role="alert">
          {cars.error.message}{' '}
          <button className="btn-secondary" onClick={() => void cars.refetch()}>
            Try again
          </button>
        </div>
      ) : cars.data.length === 0 ? (
        <p>No cars yet.</p>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {cars.data.map((car) => (
            <li key={car.id} className="rounded-lg border border-slate-200 p-4">
              <Link
                className="text-xl font-bold text-brand wrap-anywhere"
                to="/cars/$id"
                params={{ id: car.id }}
              >
                {car.name}
              </Link>
              <p>
                {car.brand} · {car.licensePlate}
              </p>
              <p>{formatBrl(car.dailyRateCents)} per day</p>
              <p>{car.available ? 'Available' : 'Unavailable'}</p>
            </li>
          ))}
        </ul>
      )}
    </PageSection>
  )
}

function useCatalogs() {
  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: ({ signal }) => listCategories(signal),
  })
  const specifications = useQuery({
    queryKey: ['specifications'],
    queryFn: ({ signal }) => listSpecifications(signal),
  })
  return { categories, specifications }
}

function CatalogStatus({ categories, specifications }: ReturnType<typeof useCatalogs>) {
  if (categories.isError || specifications.isError)
    return (
      <div role="alert">
        Could not load vehicle catalogs.{' '}
        <button
          className="btn-secondary"
          onClick={() => {
            void categories.refetch()
            void specifications.refetch()
          }}
        >
          Try again
        </button>
      </div>
    )
  return <p role="status">Loading vehicle catalogs…</p>
}

export function CarFormPage({ car }: { car?: Car }) {
  const catalogs = useCatalogs()
  const client = useQueryClient()
  const navigate = useNavigate()
  const save = useMutation({
    mutationFn: async (input: CarInput) => {
      if (!car) return createCar(input)
      const { licensePlate: _plate, ...changes } = input
      return updateCar(car.id, changes)
    },
    onSuccess: async (saved) => {
      client.setQueryData(['car', saved.id], saved)
      await client.invalidateQueries({ queryKey: ['cars'] })
      await navigate({ to: '/cars/$id', params: { id: saved.id }, replace: true })
    },
  })
  return (
    <PageSection eyebrow="Fleet" title={car ? 'Edit car' : 'New car'}>
      {save.isError && <p role="alert">{save.error.message}</p>}
      {catalogs.categories.isSuccess && catalogs.specifications.isSuccess ? (
        <CarEditor
          car={car}
          categories={catalogs.categories.data}
          specifications={catalogs.specifications.data}
          busy={save.isPending}
          onSave={(input) => save.mutate(input)}
          onCancel={() => void (car ? navigate({ to: '..' }) : navigate({ to: '/cars' }))}
        />
      ) : (
        <CatalogStatus {...catalogs} />
      )}
    </PageSection>
  )
}

export function CarRecord({ id, mode }: { id: string; mode: 'detail' | 'edit' | 'delete' }) {
  const record = useQuery({ queryKey: ['car', id], queryFn: ({ signal }) => getCar(id, signal) })
  const client = useQueryClient()
  const navigate = useNavigate()
  const remove = useMutation({
    mutationFn: () => deleteCar(id),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ['cars'] })
      await navigate({ to: '/cars', replace: true })
      client.removeQueries({ queryKey: ['car', id], exact: true })
    },
  })
  if (!record.isSuccess)
    return (
      <PageSection eyebrow="Fleet" title="Car details">
        {record.isPending ? (
          <p role="status">Loading car…</p>
        ) : (
          <div role="alert">
            {record.error.message}{' '}
            <button className="btn-secondary" onClick={() => void record.refetch()}>
              Try again
            </button>
          </div>
        )}
        <Link to="/cars">Back to cars</Link>
      </PageSection>
    )
  const car = record.data
  if (mode === 'edit') return <CarFormPage key={car.id} car={car} />
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
            onClick={() => remove.mutate()}
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

function CarDetails({ car }: { car: Car }) {
  const catalogs = useCatalogs()
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
      {catalogs.categories.isSuccess && catalogs.specifications.isSuccess ? (
        <CarCatalogDetails
          car={car}
          categories={catalogs.categories.data}
          specifications={catalogs.specifications.data}
        />
      ) : (
        <CatalogStatus {...catalogs} />
      )}
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
