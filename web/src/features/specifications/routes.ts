import { createRoute } from '@tanstack/react-router'
import { rootRoute } from '../../routes/root'
import { SpecificationsPage } from './SpecificationsPage'

const specifications = createRoute({
  getParentRoute: () => rootRoute,
  path: '/specifications',
  component: SpecificationsPage,
})
const newSpecification = createRoute({
  getParentRoute: () => rootRoute,
  path: '/specifications/new',
  component: SpecificationsPage,
})
const editSpecification = createRoute({
  getParentRoute: () => rootRoute,
  path: '/specifications/$id/edit',
  component: SpecificationsPage,
})
const deleteSpecification = createRoute({
  getParentRoute: () => rootRoute,
  path: '/specifications/$id/delete',
  component: SpecificationsPage,
})

export const specificationRoutes = [
  specifications,
  newSpecification,
  editSpecification,
  deleteSpecification,
] as const
