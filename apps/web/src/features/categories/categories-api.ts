import { protectedFetch } from '../auth/protected-fetch'
export type Category = {
  id: string
  name: string
  description: string | null
}

export async function listCategories(signal: AbortSignal): Promise<Category[]> {
  const response = await protectedFetch('/api/categories', { signal })

  if (!response.ok) {
    throw new Error(`Failed to fetch categories (${response.status})`)
  }

  return response.json()
}
