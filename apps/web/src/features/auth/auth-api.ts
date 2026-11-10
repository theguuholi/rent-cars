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

export type RegistrationInput = {
  firstName: string
  lastName: string
  userName: string
  driveLicense: string
  email: string
  password: string
  passwordConfirmation: string
}

export async function registerAccount(input: RegistrationInput): Promise<void> {
  const response = await fetch('/api/users', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(await csrfHeader()) },
    body: JSON.stringify(input),
  })
  if (response.status === 409) throw new Error('An account with these details already exists.')
  if (response.status === 422) throw new Error('Check the registration fields and try again.')
  if (!response.ok) throw new Error('Could not create your account. Try again.')
}

export async function requestPasswordReset(email: string): Promise<void> {
  const response = await fetch('/api/password-recovery', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(await csrfHeader()) },
    body: JSON.stringify({ email }),
  })
  if (!response.ok) throw new Error('Could not request a reset link. Try again.')
}

export type ResetPasswordInput = {
  token: string
  password: string
  passwordConfirmation: string
}

export async function resetPassword(input: ResetPasswordInput): Promise<void> {
  const response = await fetch('/api/password-recovery/reset', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(await csrfHeader()) },
    body: JSON.stringify(input),
  })
  if (response.status === 400)
    throw new Error('Reset link is invalid or expired. Request a new one.')
  if (response.status === 422) throw new Error('Check the new password and try again.')
  if (!response.ok) throw new Error('Could not reset your password. Try again.')
}
