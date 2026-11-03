import { useSyncExternalStore } from 'react'

export type UserProfile = { id: string; email: string; role: 'ADMIN' | 'USER' }
export type Session = { accessToken: string; user: UserProfile }
let current: Session | null = null
const listeners = new Set<() => void>()

export const session = {
  read: () => current,
  set(value: Session | null) {
    current = value
    listeners.forEach((notify) => notify())
  },
  subscribe(notify: () => void) {
    listeners.add(notify)
    return () => {
      listeners.delete(notify)
    }
  },
}

export function useSession() {
  return useSyncExternalStore(session.subscribe, session.read)
}
