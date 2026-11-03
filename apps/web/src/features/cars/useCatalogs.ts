import { useQuery } from '@tanstack/react-query'
import { listCategories } from '../categories/categories-api'
import { listSpecifications } from '../specifications/specifications-api'

export function useCatalogs() {
  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: ({ signal }) => listCategories(signal),
  })
  const specifications = useQuery({
    queryKey: ['specifications'],
    queryFn: ({ signal }) => listSpecifications(signal),
  })
  return { categories, specifications }
}
