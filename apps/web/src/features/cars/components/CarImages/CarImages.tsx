import { useRef } from 'react'
import { useForm } from '@tanstack/react-form'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useCurrentAction } from '../../../../routes/use-current-action'
import { uploadCarImages, type CarWithCatalog } from '../../cars-api'
import { CarPhoto } from '../CarPhoto/CarPhoto'

export function CarImages({ car, canUpload }: { car: CarWithCatalog; canUpload: boolean }) {
  const client = useQueryClient()
  const begin = useCurrentAction()
  const input = useRef<HTMLInputElement>(null)
  const upload = useMutation({
    mutationFn: (files: File[]) => uploadCarImages(car.id, files),
    onMutate: begin,
    onSuccess: async (_, __, current) => {
      await Promise.all([
        client.invalidateQueries({ queryKey: ['car', car.id], exact: true }),
        client.invalidateQueries({ queryKey: ['cars'] }),
      ])
      if (current()) {
        form.reset()
        input.current!.value = ''
      }
    },
  })
  const form = useForm({
    defaultValues: { images: [] as File[] },
    onSubmit: ({ value }) => upload.mutate(value.images),
  })
  return (
    <section aria-labelledby="car-images-title" className="mb-8 space-y-4">
      <h2 id="car-images-title" className="text-xl font-semibold">
        Photos
      </h2>
      {car.images.length ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {car.images.map((image, index) => (
            <CarPhoto
              key={image.id}
              carId={car.id}
              imageId={image.id}
              label={`${car.name} photo ${index + 1}`}
            />
          ))}
        </div>
      ) : (
        <p>No photos yet.</p>
      )}
      {canUpload && (
        <form
          aria-label="Upload car photos"
          noValidate
          onSubmit={(event) => {
            event.preventDefault()
            void form.handleSubmit()
          }}
        >
          <fieldset disabled={upload.isPending} className="space-y-3">
            <form.Field
              name="images"
              validators={{
                onChange: ({ value }) => {
                  if (!value.length) return 'Choose at least one photo.'
                  if (value.some((file) => !['image/jpeg', 'image/png'].includes(file.type)))
                    return 'Choose JPEG or PNG photos.'
                  if (value.some((file) => file.size === 0 || file.size > 5 * 1024 * 1024))
                    return 'Each photo must contain data and be at most 5 MiB.'
                  if (value.reduce((total, file) => total + file.size, 0) >= 30 * 1024 * 1024)
                    return 'Choose less than 30 MiB of photos per upload.'
                },
              }}
            >
              {(field) => (
                <div>
                  <label htmlFor="car-photos">Add photos</label>
                  <p id="car-photos-help" className="text-sm">
                    JPEG or PNG, up to 5 MiB each and less than 30 MiB total. Existing photos are
                    kept.
                  </p>
                  <input
                    ref={input}
                    className="control"
                    id="car-photos"
                    type="file"
                    multiple
                    accept="image/jpeg,image/png"
                    aria-describedby="car-photos-help car-photos-error"
                    aria-invalid={field.state.meta.errors.length > 0}
                    onChange={(event) => {
                      upload.reset()
                      field.handleChange(Array.from(event.target.files ?? []))
                    }}
                  />
                  <p id="car-photos-error" role="alert">
                    {field.state.meta.errors[0]}
                  </p>
                  <ul>
                    {field.state.value.map((file, index) => (
                      <li key={index}>{file.name}</li>
                    ))}
                  </ul>
                </div>
              )}
            </form.Field>
            <button className="btn-primary" type="submit">
              {upload.isPending ? 'Uploading…' : 'Upload photos'}
            </button>
          </fieldset>
          {upload.isError && <p role="alert">{upload.error.message}</p>}
          {upload.isSuccess && <p role="status">Photos uploaded.</p>}
        </form>
      )}
    </section>
  )
}
