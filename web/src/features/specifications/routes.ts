import { createRoute } from '@tanstack/react-router'
import { rootRoute } from '../../routes/root'
import { SpecificationsPage } from './SpecificationsPage'

const specifications = createRoute({
  getParentRoute: () => rootRoute,
  path: '/specifications',
  staticData: { title: 'Specifications' },
  component: SpecificationsPage,
})
const newSpecification = createRoute({
  getParentRoute: () => rootRoute,
  path: '/specifications/new',
  staticData: { title: 'New specification' },
  component: SpecificationsPage,
})
const editSpecification = createRoute({
  getParentRoute: () => rootRoute,
  path: '/specifications/$id/edit',
  staticData: { title: 'Edit specification' },
  component: SpecificationsPage,
})
const deleteSpecification = createRoute({
  getParentRoute: () => rootRoute,
  path: '/specifications/$id/delete',
  staticData: { title: 'Delete specification' },
  component: SpecificationsPage,
})

export const specificationRoutes = [
  specifications,
  newSpecification,
  editSpecification,
  deleteSpecification,
] as const
