import { useQuery } from '@tanstack/react-query'
import { listCategories } from './categories-api'

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
        <p>Could not load vehicle categories.</p>
        <button onClick={() => void categories.refetch()}>Try again</button>
      </div>
    )
  }

  if (categories.data.length === 0) {
    return <p>No vehicle categories available.</p>
  }

  return (
    <ul aria-label="Vehicle categories">
      {categories.data.map(({ name, description }) => (
        <li key={name}>
          <strong>{name}</strong>
          {description && <p>{description}</p>}
        </li>
      ))}
    </ul>
  )
}
