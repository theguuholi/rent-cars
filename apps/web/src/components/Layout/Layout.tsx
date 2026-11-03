import { useEffect } from 'react'
import { Link, Outlet, useMatches } from '@tanstack/react-router'

import { session, useSession } from '../../features/auth/session'
export function Layout() {
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
          <nav className="catalog-nav flex gap-5" aria-label="Vehicle catalogs">
            <Link to="/cars">Cars</Link>
            <Link to="/categories">Categories</Link>
            <Link to="/specifications">Specifications</Link>
            {identity ? (
              <button type="button" onClick={() => session.set(null)}>
                Sign out {identity.user.email}
              </button>
            ) : (
              <Link to="/login">Sign in</Link>
            )}
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-7xl px-4 sm:px-8">
        <Outlet />
      </main>
    </>
  )
}
