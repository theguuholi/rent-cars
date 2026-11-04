import { session } from './session'

export class AccessError extends Error {
  readonly status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export function retryQuery(failures: number, error: Error) {
  return !(error instanceof AccessError) && failures < 3
}

// Only API clients for protected endpoints call this function.
export async function protectedFetch(path: string, options: RequestInit) {
  const identity = session.read()
  const response = await fetch(
    path,
    identity
      ? {
          ...options,
          headers: { ...options.headers, Authorization: 'Bearer ' + identity.accessToken },
        }
      : options,
  )
  if (identity !== session.read())
    throw new AccessError(response.status, 'Your account changed. Try again.')
  if (response.status === 401) {
    session.set(null)
    throw new AccessError(401, 'Your session expired. Sign in again.')
  }
  if (response.status === 403)
    throw new AccessError(403, 'Your account cannot perform this action.')
  return response
}
