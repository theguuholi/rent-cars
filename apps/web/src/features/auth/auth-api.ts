import type { Session, UserProfile } from './session'

export async function signIn(email: string, password: string): Promise<Session> {
  const csrf = await fetch('/api/csrf')
  const token = csrf.headers.get('X-XSRF-TOKEN')
  if (!csrf.ok || !token) throw new Error('Could not start sign-in. Try again.')
  const login = await fetch('/api/sessions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token },
    body: JSON.stringify({ email, password }),
  })
  if (login.status === 401) throw new Error('Invalid email or password.')
  if (login.status === 403) throw new Error('Security token expired. Try signing in again.')
  if (!login.ok) throw new Error('Could not sign in. Check your details and try again.')
  const { accessToken }: { accessToken: string } = await login.json()
  const profile = await fetch('/api/sessions/me', {
    headers: { Authorization: 'Bearer ' + accessToken },
  })
  if (!profile.ok) throw new Error('Could not verify your profile. Try signing in again.')
  const user: UserProfile = await profile.json()
  return { accessToken, user }
}
