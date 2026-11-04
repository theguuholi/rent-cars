import type { Session, UserProfile } from './session'
export async function csrfHeader() {
  const csrf = await fetch('/api/csrf')
  const token = csrf.headers.get('X-XSRF-TOKEN')
  if (!csrf.ok || !token) throw new Error('Could not start sign-in. Try again.')
  return { 'X-XSRF-TOKEN': token }
}
export async function readProfile(response: Response): Promise<Session> {
  const { accessToken }: { accessToken: string } = await response.json()
  const profile = await fetch('/api/sessions/me', {
    headers: { Authorization: 'Bearer ' + accessToken },
  })
  if (!profile.ok) throw new Error('Could not verify your profile. Try signing in again.')
  const user: UserProfile = await profile.json()
  return { accessToken, user }
}
