import type { ReactNode } from 'react'

export function AccountPanel({ children }: { children: ReactNode }) {
  return (
    <div className="mx-auto my-8 max-w-lg rounded border border-line bg-white px-6 sm:my-12 sm:px-10">
      {children}
    </div>
  )
}
