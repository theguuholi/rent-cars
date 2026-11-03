import { createRoute } from '@tanstack/react-router'
import { rootRoute } from '../../routes/root'
import { CategoriesPage } from './CategoriesPage'

export const categoriesRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/categories',
  component: CategoriesPage,
})
