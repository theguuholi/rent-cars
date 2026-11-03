import { createRoute } from '@tanstack/react-router'
import { rootRoute } from '../../routes/root'
import { CategoriesPage } from './pages/CategoriesPage/CategoriesPage'

export const categoriesRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/categories',
  staticData: { title: 'Categories' },
  component: CategoriesPage,
})
