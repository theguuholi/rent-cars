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
      <header className="border-b border-slate-200">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-8 px-4 sm:px-8">
          <h1 className="py-3 text-2xl font-black tracking-tight">
            <Link to="/" aria-label="Rent Cars home">
              Rent<span className="text-orange-600">Cars</span>
            </Link>
          </h1>
          <nav
            className="catalog-nav flex flex-wrap items-center gap-x-5"
            aria-label="Vehicle catalogs"
          >
            <Link to="/cars">Cars</Link>
            {identity?.user.role === 'ADMIN' && (
              <>
                <Link to="/categories">Categories</Link>
                <Link to="/specifications">Specifications</Link>
                <Link to="/users">Users</Link>
              </>
            )}
            {identity ? (
              <button
                type="button"
                onClick={() => {
                  setLogoutError(false)
                  void signOut().catch(() => setLogoutError(true))
                }}
              >
                Sign out {identity.user.email}
              </button>
            ) : (
              <Link to="/login">Sign in</Link>
            )}
            {logoutError && <span role="alert">Could not sign out. Try again.</span>}
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-7xl px-4 sm:px-8">
        <Outlet />
      </main>
    </>
  )
}
