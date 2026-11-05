import { protectedFetch } from './protected-fetch'

const path = '/api/users/me/avatar'

export async function readAvatar(signal: AbortSignal) {
  const response = await protectedFetch(path, { signal })
  if (response.status === 404) return null
  if (!response.ok) throw new Error('Could not load your avatar.')
  return response.blob()
}

export async function saveAvatar(file: File | null) {
  let request: RequestInit = { method: 'DELETE' }
  if (file) {
    const body = new FormData()
    body.append('image', file)
    request = { method: 'PUT', body }
  }
  const response = await protectedFetch(path, request)
  if (response.status === 413) throw new Error('Choose an image of at most 5 MiB.')
  if (response.status === 422)
    throw new Error('Choose a valid JPEG or PNG of at most 4 megapixels.')
  if (!response.ok) throw new Error('Could not update your avatar. Try again.')
}
