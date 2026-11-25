import { Button } from '../../../../components/Button/Button'
import { AccessError } from '../../../auth/protected-fetch'
import type { useCatalogs } from '../../useCatalogs'

export function CatalogStatus({ categories, specifications }: ReturnType<typeof useCatalogs>) {
  const error = categories.error ?? specifications.error
  if (categories.isError || specifications.isError)
    return (
      <div role="alert">
        {error instanceof AccessError ? error.message : 'Could not load vehicle catalogs.'}{' '}
        <Button
          variant="secondary"
          onClick={() => {
            void categories.refetch()
            void specifications.refetch()
          }}
        >
          Try again
        </Button>
      </div>
    )
  return <p role="status">Loading vehicle catalogs…</p>
}
