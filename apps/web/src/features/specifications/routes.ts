import { requireAdmin } from '../auth/access'
import { createRoute } from '@tanstack/react-router'
import { rootRoute } from '../../routes/root'
import { SpecificationsPage } from './pages/SpecificationsPage/SpecificationsPage'

const specifications = createRoute({
  getParentRoute: () => rootRoute,
  path: '/specifications',
  beforeLoad: requireAdmin,
  staticData: { title: 'Specifications' },
  component: SpecificationsPage,
})
const newSpecification = createRoute({
  getParentRoute: () => rootRoute,
  path: '/specifications/new',
  beforeLoad: requireAdmin,
  staticData: { title: 'New specification' },
  component: SpecificationsPage,
})
const editSpecification = createRoute({
  getParentRoute: () => rootRoute,
  path: '/specifications/$id/edit',
  beforeLoad: requireAdmin,
  staticData: { title: 'Edit specification' },
  component: SpecificationsPage,
})
const deleteSpecification = createRoute({
  getParentRoute: () => rootRoute,
  path: '/specifications/$id/delete',
  beforeLoad: requireAdmin,
  staticData: { title: 'Delete specification' },
  component: SpecificationsPage,
})

export const specificationRoutes = [
  specifications,
  newSpecification,
  editSpecification,
  deleteSpecification,
] as const
