import { checkEditConflict } from '../../edit-conflict'
import { protectedFetch } from '../auth/protected-fetch'
export interface CarInput {
  name: string
  description: string
  brand: string
  licensePlate: string
  available: boolean
  dailyRateCents: number
  fineAmountCents: number
  categoryId: string
  specificationIds: string[]
}

export type CarPatch = Partial<Omit<CarInput, 'licensePlate'>>
export type Car = CarInput & { id: string; version: number }

export interface CarImage {
  id: string
  contentType: string
  sizeBytes: number
}

export type CarWithCatalog = Car & {
  images: CarImage[]
  category: import('../categories/categories-api').Category
  specifications: import('../specifications/specifications-api').Specification[]
}

const collection = '/api/cars'
const itemUrl = (id: string) => `${collection}/${encodeURIComponent(id)}`
const errors: Record<number, string> = {
  400: 'Check the car details and selected catalog entries.',
  404: 'This car no longer exists.',
  413: 'Images exceed the upload limit. Use up to 5 MiB per file and less than 30 MiB total.',
  409: 'A car with this license plate already exists.',
  422: 'Check the car details.',
}

const fieldLabels = new Map(
  Object.entries({
    name: 'Name',
    description: 'Description',
    brand: 'Brand',
    licensePlate: 'License plate',
    available: 'Available',
    dailyRateCents: 'Daily rate (BRL)',
    fineAmountCents: 'Fine amount (BRL)',
    categoryId: 'Category',
    specificationIds: 'Specifications',
    reference: 'Catalog selection',
  }),
)

async function carError(response: Response): Promise<string> {
  const fallback = errors[response.status] ?? `Request failed (${response.status}). Try again.`
  if (response.status !== 400 && response.status !== 422) return fallback

  try {
    const body = await response.json()
    if (typeof body.detail === 'string' && body.detail.trim()) return body.detail
    if (Array.isArray(body.errors)) return fallback
    const messages = Object.entries(body.errors).flatMap(([field, values]) =>
      Array.isArray(values)
        ? values
            .filter((value) => typeof value === 'string' && value.trim())
            .map((value) => `${fieldLabels.get(field) ?? field}: ${value}`)
        : [],
    )
    return messages.join('. ') || fallback
  } catch {
    return fallback
  }
}

async function request(
  url: string,
  options: RequestInit,
  transport: typeof protectedFetch = fetch,
): Promise<Response> {
  const response = await transport(url, options)
  if (!response.ok) {
    checkEditConflict(response)
    throw new Error(await carError(response))
  }
  return response
}

export async function listCars(signal?: AbortSignal): Promise<Car[]> {
  return (await request(collection, { signal })).json()
}

export async function getCar(id: string, signal?: AbortSignal): Promise<CarWithCatalog> {
  return (await request(itemUrl(id), { signal })).json()
}

async function writeCar(
  url: string,
  method: 'POST' | 'PATCH',
  input: Partial<CarInput>,
): Promise<Car> {
  const response = await request(
    url,
    {
      method,
      body: JSON.stringify(input),
      headers: { 'Content-Type': 'application/json' },
    },
    protectedFetch,
  )
  return response.json()
}

export function createCar(input: CarInput): Promise<Car> {
  return writeCar(collection, 'POST', input)
}

export function updateCar(id: string, input: CarPatch & { expectedVersion: number }): Promise<Car> {
  return writeCar(itemUrl(id), 'PATCH', input)
}

export async function deleteCar(id: string): Promise<void> {
  await request(itemUrl(id), { method: 'DELETE' }, protectedFetch)
}

export function carImageUrl(carId: string, imageId: string): string {
  return `${itemUrl(carId)}?imageId=${encodeURIComponent(imageId)}`
}

export async function uploadCarImages(id: string, files: File[]): Promise<CarWithCatalog> {
  const body = new FormData()
  for (const file of files) body.append('images', file)
  const response = await request(`${itemUrl(id)}/images`, { method: 'PATCH', body }, protectedFetch)
  return response.json()
}
