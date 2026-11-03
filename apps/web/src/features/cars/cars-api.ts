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
export type Car = CarInput & { id: string }

const collection = '/api/cars'
const itemUrl = (id: string) => `${collection}/${encodeURIComponent(id)}`
const errors: Record<number, string> = {
  400: 'Check the car details and selected catalog entries.',
  404: 'This car no longer exists.',
  409: 'A car with this license plate already exists.',
  422: 'Check the car details. The license plate cannot change after registration.',
}

async function request(url: string, options: RequestInit): Promise<Response> {
  const response = await fetch(url, options)
  if (!response.ok) {
    throw new Error(errors[response.status] ?? `Request failed (${response.status}). Try again.`)
  }
  return response
}

export async function listCars(signal?: AbortSignal): Promise<Car[]> {
  return (await request(collection, { signal })).json()
}

export async function getCar(id: string, signal?: AbortSignal): Promise<Car> {
  return (await request(itemUrl(id), { signal })).json()
}

async function writeCar(
  url: string,
  method: 'POST' | 'PATCH',
  input: Partial<CarInput>,
): Promise<Car> {
  const response = await request(url, {
    method,
    body: JSON.stringify(input),
    headers: { 'Content-Type': 'application/json' },
  })
  return response.json()
}

export function createCar(input: CarInput): Promise<Car> {
  return writeCar(collection, 'POST', input)
}

export function updateCar(id: string, input: CarPatch): Promise<Car> {
  return writeCar(itemUrl(id), 'PATCH', input)
}

export async function deleteCar(id: string): Promise<void> {
  await request(itemUrl(id), { method: 'DELETE' })
}
