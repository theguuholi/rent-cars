import { Button } from '../Button/Button'
import { useEffect, useState } from 'react'
import { Link, Outlet, useMatches } from '@tanstack/react-router'

import { signOut } from '../../features/auth/renewal'
import { useSession } from '../../features/auth/session'
export function Layout() {
  const [logoutError, setLogoutError] = useState(false)
  const identity = useSession()
  const title = useMatches({ select: (matches) => matches.at(-1)!.staticData.title })
  useEffect(() => {
    document.title = title ? `${title} | Rent Cars` : 'Rent Cars'
  }, [title])
  return (
    <>
      <a
        href="#main-content"
        className="sr-only z-50 rounded bg-white px-4 py-3 text-ink focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
      >
        Skip to content
      </a>
      <header className="border-b border-line/60 bg-white text-ink">
        <div className="mx-auto flex max-w-7xl flex-col items-start gap-x-10 px-4 sm:flex-row sm:flex-wrap sm:items-center sm:px-8">
          <h1 className="py-4 font-heading text-2xl font-bold tracking-tight">
            <Link to="/" aria-label="Rent Cars home">
              Rent<span className="text-brand">Cars</span>
            </Link>
          </h1>
          <nav
            className="flex min-w-0 flex-1 flex-wrap items-center gap-x-6 gap-y-1 text-sm [&>a]:border-b-2 [&>a]:border-transparent [&>a]:py-3 sm:[&>a]:py-5 [&>a]:font-medium [&>a:hover]:text-brand [&>a[aria-current=page]]:border-brand [&>a[aria-current=page]]:text-brand"
            aria-label="Vehicle catalogs"
          >
            <Link to="/cars">Cars</Link>
            {identity?.user.role === 'USER' && <Link to="/rentals">My rentals</Link>}
            {identity?.user.role === 'ADMIN' && (
              <>
                <Link to="/categories">Categories</Link>
                <Link to="/specifications">Specifications</Link>
                <Link to="/users">Users</Link>
              </>
            )}
            {identity ? (
              <Button
                className="my-2 max-w-full rounded-full bg-surface px-4 py-2.5 text-left text-sm text-ink wrap-anywhere hover:bg-brand-soft lg:ml-auto"
                type="button"
                onClick={() => {
                  setLogoutError(false)
                  void signOut().catch(() => setLogoutError(true))
                }}
              >
                Sign out <span className="sr-only sm:not-sr-only">{identity.user.email}</span>
              </Button>
            ) : (
              <Link to="/login">Sign in</Link>
            )}
            {logoutError && <span role="alert">Could not sign out. Try again.</span>}
          </nav>
        </div>
      </header>
      <main
        id="main-content"
        tabIndex={-1}
        className="mx-auto max-w-7xl px-4 pb-12 focus:outline-none sm:px-8"
      >
        <Outlet />
      </main>
    </>
  )
}
