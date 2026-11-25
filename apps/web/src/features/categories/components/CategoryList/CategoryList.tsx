import { Button } from '../../../../components/Button/Button'
import { AccessError } from '../../../auth/protected-fetch'
import { useQuery } from '@tanstack/react-query'
import { listCategories } from '../../categories-api'

export function CategoryList() {
  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: ({ signal }) => listCategories(signal),
  })

  if (categories.isPending) {
    return <p role="status">Loading categories…</p>
  }

  if (categories.isError) {
    return (
      <div role="alert">
        <p>
          {categories.error instanceof AccessError
            ? categories.error.message
            : 'Could not load vehicle categories.'}
        </p>
        <Button variant="secondary" onClick={() => void categories.refetch()}>
          Try again
        </Button>
      </div>
    )
  }

  if (categories.data.length === 0) {
    return <p>No vehicle categories available.</p>
  }

  return (
    <ul className="grid gap-4 sm:grid-cols-2" aria-label="Vehicle categories">
      {categories.data.map(({ name, description }) => (
        <li key={name} className="rounded-3xl bg-white shadow-sm p-5">
          <strong>{name}</strong>
          {description && <p className="mt-1 text-muted">{description}</p>}
        </li>
      ))}
    </ul>
  )
}
