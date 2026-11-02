import {
  createRootRoute,
  createRoute,
  createRouter,
  type RouterHistory,
} from '@tanstack/react-router'
import { Layout } from './App'
import { NotFoundPage } from './NotFoundPage'
import { CategoriesPage } from './features/categories/CategoriesPage'
import { SpecificationsPage } from './features/specifications/SpecificationsPage'
const root = createRootRoute({ component: Layout, notFoundComponent: NotFoundPage })
const home = createRoute({ getParentRoute: () => root, path: '/', component: () => null })
const categories = createRoute({
  getParentRoute: () => root,
  path: '/categories',
  component: CategoriesPage,
})
const specifications = createRoute({
  getParentRoute: () => root,
  path: '/specifications',
  component: SpecificationsPage,
})
const newSpecification = createRoute({
  getParentRoute: () => root,
  path: '/specifications/new',
  component: SpecificationsPage,
})
const editSpecification = createRoute({
  getParentRoute: () => root,
  path: '/specifications/$id/edit',
  component: SpecificationsPage,
})
const deleteSpecification = createRoute({
  getParentRoute: () => root,
  path: '/specifications/$id/delete',
  component: SpecificationsPage,
})
const tree = root.addChildren([
  home,
  categories,
  specifications,
  newSpecification,
  editSpecification,
  deleteSpecification,
])
export function createAppRouter(history?: RouterHistory) {
  return createRouter({ routeTree: tree, history })
}
declare module '@tanstack/react-router' {
  interface Register {
    router: ReturnType<typeof createAppRouter>
  }
}
