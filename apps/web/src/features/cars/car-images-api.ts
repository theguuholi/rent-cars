import { protectedFetch } from '../auth/protected-fetch'

export type CarImage = { id: string }
const collection = (carId: string) => `/api/cars/${encodeURIComponent(carId)}/images`
export const carImageUrl = (carId: string, imageId: string) =>
  `${collection(carId)}/${encodeURIComponent(imageId)}`

export async function listCarImages(carId: string, signal: AbortSignal): Promise<CarImage[]> {
  const response = await fetch(collection(carId), { signal })
  if (!response.ok) throw new Error('Could not load car photos.')
  return response.json()
}

const errors: Record<number, string> = {
  403: 'Only administrators can change car photos.',
  404: 'This car or photo no longer exists.',
  413: 'Choose an image of at most 5 MiB.',
  422: 'Choose a valid JPEG or PNG of at most 4 megapixels.',
}

async function write(url: string, options: RequestInit) {
  const response = await protectedFetch(url, options)
  if (!response.ok)
    throw new Error(errors[response.status] ?? 'Could not update car photos. Try again.')
}

export function uploadCarImage(carId: string, file: File) {
  const body = new FormData()
  body.append('image', file)
  return write(collection(carId), { method: 'POST', body })
}

export function deleteCarImage(carId: string, imageId: string) {
  return write(carImageUrl(carId, imageId), { method: 'DELETE' })
}
