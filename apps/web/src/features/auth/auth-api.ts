import { deviceKey, withCookieLock } from './renewal'
import { csrfHeader, readProfile } from './session-http'
import type { Session } from './session'

export function signIn(email: string, password: string): Promise<Session> {
  return withCookieLock(async () => {
    const login = await fetch('/api/sessions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(await csrfHeader()) },
      body: JSON.stringify({ email, password }),
    })
    if (login.status === 401) throw new Error('Invalid email or password.')
    if (login.status === 403) throw new Error('Security token expired. Try signing in again.')
    if (!login.ok) throw new Error('Could not sign in. Check your details and try again.')
    const identity = await readProfile(login)
    localStorage.setItem(deviceKey, crypto.randomUUID())
    return identity
  })
}
