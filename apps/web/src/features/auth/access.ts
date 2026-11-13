import { redirect } from '@tanstack/react-router'
import { session } from './session'

declare module '@tanstack/react-router' {
  interface HistoryState {
    afterLogin?: string
  }
}

export function requireAdmin({
  context,
  location,
}: {
  context: { session: typeof session }
  location: { pathname: string }
}) {
  const identity = context.session.read()
  if (!identity)
    throw redirect({ to: '/login', replace: true, state: { afterLogin: location.pathname } })
  if (identity.user.role !== 'ADMIN') throw redirect({ to: '/forbidden', replace: true })
}

// Accept a pathname only; the guard stores it in internal browser history state.
export function loginDestination(path: string | undefined, role: string) {
  if (
    path &&
    /^\/(?!\/)[^\\\s]*$/.test(path) &&
    (role === 'ADMIN' || (role === 'USER' && path === '/rentals'))
  )
    return path
  return '/cars'
}

export function requireDriver({
  context,
  location,
}: {
  context: { session: typeof session }
  location: { pathname: string }
}) {
  const identity = context.session.read()
  if (!identity)
    throw redirect({ to: '/login', replace: true, state: { afterLogin: location.pathname } })
  if (identity.user.role !== 'USER') throw redirect({ to: '/forbidden', replace: true })
}
