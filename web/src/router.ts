import {
  createRootRoute,
  createRoute,
  createRouter,
  type RouterHistory,
} from '@tanstack/react-router'
import { Layout, CategoriesPage } from './App'
import { Specifications } from './features/specifications/Specifications'
const root = createRootRoute({ component: Layout })
const home = createRoute({ getParentRoute: () => root, path: '/', component: () => null })
const categories = createRoute({
  getParentRoute: () => root,
  path: '/categories',
  component: CategoriesPage,
})
const specifications = createRoute({
  getParentRoute: () => root,
  path: '/specifications',
  component: Specifications,
})
const tree = root.addChildren([home, categories, specifications])
export function createAppRouter(history?: RouterHistory) {
  return createRouter({ routeTree: tree, history })
}
declare module '@tanstack/react-router' {
  interface Register {
    router: ReturnType<typeof createAppRouter>
  }
}
