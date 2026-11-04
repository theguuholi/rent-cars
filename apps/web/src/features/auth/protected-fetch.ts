import { deviceKey, renew } from './renewal'
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
  const generation = session.generation()
  const identity = session.read()
  const send = async (token?: string) => {
    const response = await fetch(
      path,
      token
        ? { ...options, headers: { ...options.headers, Authorization: 'Bearer ' + token } }
        : options,
    )
    if (generation !== session.generation())
      throw new AccessError(response.status, 'Your account changed. Try again.')
    return response
  }
  let response = await send(identity?.accessToken)
  if (response.status === 401 && identity && localStorage.getItem(deviceKey)) {
    if (identity === session.read()) await renew()
    const next = session.read()
    if (generation !== session.generation())
      throw new AccessError(401, 'Your session changed. Sign in again.')
    response = await send(next!.accessToken)
  }
  if (response.status === 401) {
    session.set(null)
    throw new AccessError(401, 'Your session expired. Sign in again.')
  }
  if (response.status === 403)
    throw new AccessError(403, 'Your account cannot perform this action.')
  return response
}
