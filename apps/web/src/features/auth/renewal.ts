import { csrfHeader, readProfile } from './session-http'
import { session } from './session'

// Only this opaque login identifier is shared between tabs. Credentials stay out of storage.
export const deviceKey = 'rent-cars.login'
export function withCookieLock<T>(action: () => Promise<T>) {
  return navigator.locks.request('rent-cars.session', action)
}

async function exchange(path: string, method: string) {
  return fetch('/api/sessions/' + path, { method, headers: await csrfHeader() })
}

async function rotate() {
  const generation = session.generation()
  const login = localStorage.getItem(deviceKey)
  return withCookieLock(async () => {
    if (generation !== session.generation() || login !== localStorage.getItem(deviceKey)) return
    const response = await exchange('refresh', 'POST')
    if (generation !== session.generation()) return
    if (response.status === 401) {
      localStorage.removeItem(deviceKey)
      session.set(null)
      return
    }
    if (!response.ok) throw new Error('Could not renew your session. Try again.')
    const next = await readProfile(response)
    if (generation === session.generation()) session.renew(next)
  })
}

let pending: Promise<void> | undefined
export function renew() {
  pending ??= rotate().finally(() => {
    pending = undefined
  })
  return pending
}

export async function signOut() {
  const login = localStorage.getItem(deviceKey)
  if (login) {
    await withCookieLock(async () => {
      if (login !== localStorage.getItem(deviceKey)) return
      const response = await exchange('current', 'DELETE')
      if (!response.ok) throw new Error('Could not sign out. Try again.')
      localStorage.removeItem(deviceKey)
    })
  }
  session.set(null)
}

export async function restore() {
  if (localStorage.getItem(deviceKey)) await renew()
}

export function onDeviceChange(event: StorageEvent) {
  if (event.key === deviceKey) window.location.reload()
}
