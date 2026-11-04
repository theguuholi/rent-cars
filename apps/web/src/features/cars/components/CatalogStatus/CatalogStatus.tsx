import { AccessError } from '../../../auth/protected-fetch'
import type { useCatalogs } from '../../useCatalogs'

export function CatalogStatus({ categories, specifications }: ReturnType<typeof useCatalogs>) {
  const error = categories.error ?? specifications.error
  if (categories.isError || specifications.isError)
    return (
      <div role="alert">
        {error instanceof AccessError ? error.message : 'Could not load vehicle catalogs.'}{' '}
        <button
          className="btn-secondary"
          onClick={() => {
            void categories.refetch()
            void specifications.refetch()
          }}
        >
          Try again
        </button>
      </div>
    )
  return <p role="status">Loading vehicle catalogs…</p>
}
