import { checkEditConflict } from '../../edit-conflict'
export type SpecificationInput = { name: string; description: string }
export type Specification = SpecificationInput & { id: string; version: number }
export type SpecificationEdit = Partial<SpecificationInput> & {
  expectedVersion?: number
}
const path = '/api/specifications'

async function request(url: string, options: RequestInit) {
  const response = await fetch(url, options)
  if (!response.ok) {
    checkEditConflict(response)
    throw new Error(
      (
        {
          404: 'This specification no longer exists. Refresh the catalog.',
          409: 'A specification with this name already exists.',
          422: 'Check the name and description. Both are required and the uppercase name must fit in 255 characters.',
        } as Record<number, string>
      )[response.status] ?? `Request failed (${response.status}). Try again.`,
    )
  }
  return response
}

export async function listSpecifications(signal: AbortSignal): Promise<Specification[]> {
  return (await request(path, { signal })).json()
}

export async function saveSpecification(
  input: SpecificationEdit,
  id?: string,
): Promise<Specification> {
  const response = await request(id ? `${path}/${encodeURIComponent(id)}` : path, {
    method: id ? 'PATCH' : 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
  return response.json()
}

export async function deleteSpecification(id: string): Promise<void> {
  await request(`${path}/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
