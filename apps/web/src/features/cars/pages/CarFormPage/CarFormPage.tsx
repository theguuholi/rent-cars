import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { PageSection } from '../../../../components/PageSection/PageSection'
import { CarEditor } from '../../components/CarEditor/CarEditor'
import { CatalogStatus } from '../../components/CatalogStatus/CatalogStatus'
import { changedCarFields } from '../../changed-car-fields'
import { useCatalogs } from '../../useCatalogs'
import { createCar, updateCar, type Car, type CarInput } from '../../cars-api'

export function CarFormPage({ car: loadedCar }: { car?: Car }) {
  // CarRecord keys this form by ID. A refetch must not replace the starting point of an open edit.
  const [car] = useState(loadedCar)
  const catalogs = useCatalogs()
  const client = useQueryClient()
  const navigate = useNavigate()
  const save = useMutation({
    mutationFn: async (input: CarInput) => {
      if (!car) return createCar(input)
      return updateCar(car.id, changedCarFields(car, input))
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
