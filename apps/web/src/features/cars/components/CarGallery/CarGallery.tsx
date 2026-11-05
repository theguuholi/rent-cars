import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCurrentAction } from '../../../../routes/use-current-action'
import { useSession } from '../../../auth/session'
import { carImageUrl, deleteCarImage, listCarImages, uploadCarImage } from '../../car-images-api'

export function CarGallery({ carId }: { carId: string }) {
  const isAdmin = useSession()?.user.role === 'ADMIN'
  const [file, setFile] = useState<File | null>(null)
  const client = useQueryClient()
  const queryKey = ['car-images', carId]
  const gallery = useQuery({ queryKey, queryFn: ({ signal }) => listCarImages(carId, signal) })
  const begin = useCurrentAction()
  const change = useMutation({
    onMutate: begin,
    mutationFn: (image: File | string) =>
      typeof image === 'string' ? deleteCarImage(carId, image) : uploadCarImage(carId, image),
    onSuccess: async (_, _image, current) => {
      if (!current()) return
      setFile(null)
      await client.invalidateQueries({ queryKey })
    },
  })
  return (
    <section className="mt-8" aria-label="Car photos">
      <h2 className="text-xl font-semibold">Photos</h2>
      {gallery.isPending && <p role="status">Loading car photos…</p>}
      {gallery.isError && (
        <p role="alert">
          {gallery.error.message}{' '}
          <button type="button" onClick={() => void gallery.refetch()}>
            Try again
          </button>
        </p>
      )}
      {gallery.isSuccess && (
        <>
          {gallery.data.length === 0 && <p>No photos yet.</p>}
          <ul className="my-4 grid gap-4 sm:grid-cols-2" aria-label="Car photos">
            {gallery.data.map((image, index) => (
              <li key={image.id}>
                <img
                  className="aspect-video w-full rounded-lg object-contain"
                  src={carImageUrl(carId, image.id)}
                  alt={`Car photo ${index + 1}`}
                />
                {isAdmin && (
                  <button
                    className="btn-secondary mt-2"
                    type="button"
                    disabled={change.isPending}
                    onClick={() => change.mutate(image.id)}
                  >
                    Remove photo {index + 1}
                  </button>
                )}
              </li>
            ))}
          </ul>
          {isAdmin && (
            <div className="grid max-w-md gap-3">
              <label>
                Add car photo
                <input
                  className="control"
                  type="file"
                  accept="image/jpeg,image/png"
                  disabled={change.isPending}
                  onChange={(event) => {
                    setFile(event.target.files?.item(0) ?? null)
                    event.target.value = ''
                    change.reset()
                  }}
                />
              </label>
              <p>JPEG or PNG, up to 5 MiB and 4 megapixels. Upload one photo at a time.</p>
              {file && <p>{file.name}</p>}
              <button
                className="btn-primary"
                type="button"
                disabled={!file || change.isPending}
                onClick={() => change.mutate(file!)}
              >
                Upload photo
              </button>
            </div>
          )}
        </>
      )}
      {change.isError && <p role="alert">{change.error.message}</p>}
      {change.isSuccess && <p role="status">Car photos updated.</p>}
    </section>
  )
}
