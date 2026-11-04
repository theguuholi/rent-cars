import { useSyncExternalStore } from 'react'

export type UserProfile = { id: string; email: string; role: 'ADMIN' | 'USER' }
export type Session = { accessToken: string; user: UserProfile }
let current: Session | null = null
let generation = Symbol()
const listeners = new Set<() => void>()

export const session = {
  read: () => current,
  generation: () => generation,
  renew(next: Session) {
    if (
      current?.user.id === next.user.id &&
      current.user.role === next.user.role &&
      current.user.email === next.user.email
    )
      current = next
    else session.set(next)
  },
  set(value: Session | null) {
    generation = Symbol()
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
