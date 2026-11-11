import { protectedFetch } from '../auth/protected-fetch'
import type { RegistrationInput } from '../auth/auth-api'

export type ManagedUser = {
  id: string
  email: string
  role: 'ADMIN' | 'USER'
  firstName: string | null
  lastName: string | null
  userName: string | null
  driveLicense: string | null
}

export type UserUpdate = Omit<ManagedUser, 'id'>

const collection = '/api/admin/users'
const item = (id: string) => `${collection}/${encodeURIComponent(id)}`

async function request(path: string, options: RequestInit): Promise<Response> {
  const response = await protectedFetch(path, options)
  if (response.ok) return response
  const messages: Record<number, string> = {
    404: 'This user no longer exists.',
    409: 'These details already exist, or this account cannot be changed or deleted.',
    422: 'Check the user details and try again.',
  }
  throw new Error(messages[response.status] ?? 'Could not manage this user. Try again.')
}

export async function listUsers(signal?: AbortSignal): Promise<ManagedUser[]> {
  return (await request(collection, { signal })).json()
}

export async function getUser(id: string, signal?: AbortSignal): Promise<ManagedUser> {
  return (await request(item(id), { signal })).json()
}

export async function createUser(
  user: RegistrationInput,
  role: ManagedUser['role'],
): Promise<ManagedUser> {
  return (
    await request(collection, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ user, role }),
    })
  ).json()
}

export async function updateUser(id: string, user: UserUpdate): Promise<ManagedUser> {
  return (
    await request(item(id), {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(user),
    })
  ).json()
}

export async function deleteUser(id: string): Promise<void> {
  await request(item(id), { method: 'DELETE' })
}
