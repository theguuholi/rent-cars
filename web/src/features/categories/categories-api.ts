export type Category = {
  name: string
  description: string | null
}

export async function listCategories(signal: AbortSignal): Promise<Category[]> {
  const response = await fetch('/api/categories', { signal })

  if (!response.ok) {
    throw new Error(`Failed to fetch categories (${response.status})`)
  }

  return response.json()
}